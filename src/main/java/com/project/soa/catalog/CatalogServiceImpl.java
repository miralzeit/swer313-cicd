package com.project.soa.catalog;

import com.project.soa.auth.user.User;
import com.project.soa.auth.user.UserInternalService;
import com.project.soa.audit.AuditLogService;
import com.project.soa.booking.BookingRepository;
import com.project.soa.booking.BookingStatus;
import com.project.soa.common.exception.BusinessRuleException;
import com.project.soa.common.exception.ResourceNotFoundException;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class CatalogServiceImpl implements CatalogService, CatalogInternalService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("name", "rating", "city", "createdAt");

    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final UserInternalService userService;
    private final AuditLogService auditLogService;

    public CatalogServiceImpl(HotelRepository hotelRepository,
                              RoomTypeRepository roomTypeRepository,
                              RoomRepository roomRepository,
                              BookingRepository bookingRepository,
                              UserInternalService userService,
                              AuditLogService auditLogService) {
        this.hotelRepository    = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository     = roomRepository;
        this.bookingRepository  = bookingRepository;
        this.userService        = userService;
        this.auditLogService    = auditLogService;
    }

    // ── Guest: browse / search ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public HotelListResponseDto browseActiveHotels(int page, int size,
                                                   String name, String city, String area,
                                                   String country,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   String roomType, List<String> amenities,
                                                   Integer minRating,
                                                   String sortBy, String sortDir) {
        int clampedSize = Math.min(size, 100);
        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        Specification<Hotel> spec = buildSpec(
                name, city, area, country,
                minPrice, maxPrice, roomType, amenities, minRating);

        Page<Hotel> hotelPage = hotelRepository.findAll(spec, pageable);

        List<HotelResponseDto> content = hotelPage.getContent().stream()
                .map(HotelMapper::toDtoSummary).toList();

        return new HotelListResponseDto(content, hotelPage.getNumber(),
                hotelPage.getSize(), hotelPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponseDto getActiveHotelDetails(UUID id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", id));
        if (hotel.getStatus() != CatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("Hotel", id);
        }
        return HotelMapper.toDto(hotel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomTypeResponseDto> getRoomTypesForActiveHotel(UUID hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelId));
        if (hotel.getStatus() != CatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("Hotel", hotelId);
        }
        return roomTypeRepository.findByHotelId(hotelId).stream()
                .filter(rt -> rt.getStatus() == CatalogStatus.ACTIVE)
                .map(RoomTypeMapper::toDto).toList();
    }

    // ── Manager: read ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<HotelResponseDto> getAllHotelsForManager(UUID managerId) {
        return hotelRepository.findByManagerId(managerId).stream()
                .map(HotelMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponseDto getHotelByIdForManager(UUID id, UUID managerId) {
        return HotelMapper.toDto(requireOwnedHotel(id, managerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomTypeResponseDto> getRoomTypesByHotelForManager(UUID hotelId, UUID managerId) {
        requireOwnedHotel(hotelId, managerId);
        return roomTypeRepository.findByHotelId(hotelId).stream()
                .map(RoomTypeMapper::toDto).toList();
    }

    // ── Hotel CRUD ────────────────────────────────────────────────────────────

    @Override
    public Hotel createHotel(HotelRequestDto dto, UUID managerId) {
        User manager = userService.getById(managerId);
        Hotel hotel = HotelMapper.toEntity(dto);
        hotel.setManager(manager);
        return hotelRepository.save(hotel);
    }

    @Override
    public Hotel updateHotel(UUID id, HotelRequestDto dto, UUID managerId) {
        Hotel hotel = requireOwnedHotel(id, managerId);
        hotel.setName(dto.getName());
        hotel.setLocation(dto.getLocation());
        hotel.setDescription(dto.getDescription());
        hotel.setCity(dto.getCity());
        hotel.setCountry(dto.getCountry());
        hotel.setAddress(dto.getAddress());
        hotel.setRating(dto.getRating());
        return hotelRepository.save(hotel);
    }

    @Override
    public void deleteHotel(UUID id, UUID managerId) {
        Hotel hotel = requireOwnedHotel(id, managerId);
        boolean hasActiveBookings = hotel.getRoomTypes().stream()
                .anyMatch(rt -> bookingRepository
                        .countActiveOrUpcomingNotCancelledForRoomType(rt.getId(), LocalDate.now()) > 0);
        if (hasActiveBookings) {
            throw new BusinessRuleException(
                    "Cannot delete hotel: it has active or upcoming bookings.");
        }
        hotelRepository.delete(hotel);
    }

    // ── Room type CRUD ────────────────────────────────────────────────────────

    @Override
    public RoomType createRoomType(UUID hotelId, RoomTypeRequestDto dto, UUID managerId) {
        Hotel hotel = requireOwnedHotel(hotelId, managerId);
        return roomTypeRepository.save(RoomTypeMapper.toEntity(dto, hotel));
    }

    @Override
    public RoomType updateRoomType(UUID hotelId, UUID roomTypeId,
                                   RoomTypeRequestDto dto, UUID managerId) {
        requireOwnedHotel(hotelId, managerId);
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", roomTypeId));
        if (!rt.getHotel().getId().equals(hotelId)) {
            throw new BusinessRuleException("RoomType does not belong to this hotel.");
        }
        rt.setName(dto.getName());
        rt.setDescription(dto.getDescription());
        rt.setCapacity(dto.getCapacity());
        rt.setBasePrice(dto.getBasePrice());
        rt.setTotalRooms(dto.getTotalRooms());
        if (dto.getAmenities() != null) rt.setAmenities(dto.getAmenities());
        return roomTypeRepository.save(rt);
    }

    @Override
    public void deleteRoomType(UUID hotelId, UUID roomTypeId, UUID managerId) {
        requireOwnedHotel(hotelId, managerId);
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", roomTypeId));
        if (!rt.getHotel().getId().equals(hotelId)) {
            throw new BusinessRuleException("RoomType does not belong to this hotel.");
        }
        long active = bookingRepository
                .countActiveOrUpcomingNotCancelledForRoomType(roomTypeId, LocalDate.now());
        if (active > 0) {
            throw new BusinessRuleException(
                    "Cannot delete room type: it has " + active + " active or upcoming booking(s).");
        }
        roomTypeRepository.delete(rt);
    }

    @Override
    public Room createRoom(UUID roomTypeId, RoomRequestDto dto, UUID callerId, boolean admin) {
        RoomType roomType = requireManagedRoomType(roomTypeId, callerId, admin);
        String roomNumber = normalizeRoomNumber(dto.getRoomNumber());
        UUID hotelId = roomType.getHotel().getId();
        if (roomRepository.existsByHotelIdAndRoomNumber(hotelId, roomNumber)) {
            throw new BusinessRuleException("Room number already exists in this hotel");
        }

        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setStatus(dto.getStatus() != null ? dto.getStatus() : RoomStatus.AVAILABLE);
        room.setRoomType(roomType);
        room.setHotel(roomType.getHotel());
        Room saved = roomRepository.save(room);
        auditLogService.log("ROOM_CREATED", callerId, "Room", saved.getId(), "Room " + saved.getRoomNumber() + " created");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponseDto> getRoomsForRoomType(UUID roomTypeId, UUID callerId, boolean admin) {
        requireManagedRoomType(roomTypeId, callerId, admin);
        return roomRepository.findByRoomTypeIdOrderByRoomNumberAsc(roomTypeId)
                .stream()
                .map(RoomMapper::toDto)
                .toList();
    }

    @Override
    public Room updateRoom(UUID roomId, UpdateRoomRequestDto dto, UUID callerId, boolean admin) {
        Room room = requireManagedRoom(roomId, callerId, admin);

        if (dto.getRoomNumber() != null) {
            String roomNumber = normalizeRoomNumber(dto.getRoomNumber());
            if (roomRepository.existsByHotelIdAndRoomNumberAndIdNot(
                    room.getHotel().getId(), roomNumber, room.getId())) {
                throw new BusinessRuleException("Room number already exists in this hotel");
            }
            room.setRoomNumber(roomNumber);
        }
        if (dto.getStatus() != null) {
            room.setStatus(dto.getStatus());
        }

        Room saved = roomRepository.save(room);
        auditLogService.log("ROOM_UPDATED", callerId, "Room", saved.getId(), "Room " + saved.getRoomNumber() + " updated");
        return saved;
    }

    @Override
    public void deleteRoom(UUID roomId, UUID callerId, boolean admin) {
        Room room = requireManagedRoom(roomId, callerId, admin);
        if (room.getStatus() == RoomStatus.OCCUPIED
                || bookingRepository.existsByRoomIdAndStatus(roomId, BookingStatus.CHECKED_IN)) {
            throw new BusinessRuleException("Cannot delete room: it is occupied or assigned to an active booking");
        }

        roomRepository.delete(room);
        auditLogService.log("ROOM_DELETED", callerId, "Room", roomId, "Room " + room.getRoomNumber() + " deleted");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Hotel requireOwnedHotel(UUID hotelId, UUID managerId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelId));
        if (hotel.getManager() == null || !hotel.getManager().getId().equals(managerId)) {
            throw new BusinessRuleException("Access denied: you do not own this hotel.");
        }
        return hotel;
    }

    private RoomType requireManagedRoomType(UUID roomTypeId, UUID callerId, boolean admin) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", roomTypeId));
        if (!admin) {
            Hotel hotel = roomType.getHotel();
            if (hotel == null || hotel.getManager() == null || !hotel.getManager().getId().equals(callerId)) {
                throw new BusinessRuleException("Access denied: you do not own this hotel.");
            }
        }
        return roomType;
    }

    private Room requireManagedRoom(UUID roomId, UUID callerId, boolean admin) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
        if (!admin) {
            Hotel hotel = room.getHotel();
            if (hotel == null || hotel.getManager() == null || !hotel.getManager().getId().equals(callerId)) {
                throw new BusinessRuleException("Access denied: you do not own this hotel.");
            }
        }
        return room;
    }

    private String normalizeRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new BusinessRuleException("Room number is required");
        }
        return roomNumber.trim();
    }

    private Specification<Hotel> buildSpec(String name, String city, String area,
                                           String country,
                                           BigDecimal minPrice, BigDecimal maxPrice,
                                           String roomTypeName, List<String> amenities,
                                           Integer minRating) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always active only
            predicates.add(cb.equal(root.get("status"), CatalogStatus.ACTIVE));

            // ── NEW: name search (partial, case-insensitive) ──────────────────
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        "%" + name.trim().toLowerCase() + "%"));
            }

            // ── NEW: area search against location field (partial) ─────────────
            if (area != null && !area.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("location")),
                        "%" + area.trim().toLowerCase() + "%"));
            }

            // ── ORIGINAL: city exact match ────────────────────────────────────
            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("city")),
                        city.trim().toLowerCase()));
            }

            // ── ORIGINAL: country exact match ─────────────────────────────────
            if (country != null && !country.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("country")),
                        country.trim().toLowerCase()));
            }

            // ── ORIGINAL: rating ──────────────────────────────────────────────
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
            }

            // ── ORIGINAL: room-type join for price / type / amenities ─────────
            boolean needsJoin = minPrice != null || maxPrice != null
                    || (roomTypeName != null && !roomTypeName.isBlank())
                    || (amenities != null && !amenities.isEmpty());

            if (needsJoin) {
                query.distinct(true);
                Join<Hotel, RoomType> rt = root.join("roomTypes", JoinType.INNER);
                rt.on(cb.equal(rt.get("status"), CatalogStatus.ACTIVE));

                if (minPrice != null) {
                    predicates.add(cb.greaterThanOrEqualTo(rt.get("basePrice"), minPrice));
                }
                if (maxPrice != null) {
                    predicates.add(cb.lessThanOrEqualTo(rt.get("basePrice"), maxPrice));
                }
                if (roomTypeName != null && !roomTypeName.isBlank()) {
                    predicates.add(cb.like(
                            cb.lower(rt.get("name")),
                            "%" + roomTypeName.toLowerCase() + "%"));
                }
                if (amenities != null && !amenities.isEmpty()) {
                    Join<RoomType, String> amenityJoin = rt.join("amenities", JoinType.INNER);
                    predicates.add(amenityJoin.in(amenities));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "name";
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }
    // ── CatalogInternalService ────────────────────────────────────────────────

    @Override
    public RoomType getRoomTypeForUpdate(UUID id) {
        return roomTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", id));
    }

    @Override
    public Room assignAvailableRoom(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        Room room = roomRepository.findAvailableRoomsForAssignment(roomTypeId, checkIn, checkOut)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("No available rooms for this room type"));

        room.setStatus(RoomStatus.OCCUPIED);
        return roomRepository.save(room);
    }

    @Override
    public void releaseRoom(UUID roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
        room.setStatus(RoomStatus.AVAILABLE);
        roomRepository.save(room);
    }

    @Override
    public Optional<Hotel> findHotelById(UUID id) {
        return hotelRepository.findById(id);
    }

    @Override
    public long countHotels() {
        return hotelRepository.count();
    }

}
