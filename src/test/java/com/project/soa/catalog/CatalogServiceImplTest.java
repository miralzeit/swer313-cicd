package com.project.soa.catalog;

import com.project.soa.auth.user.User;
import com.project.soa.auth.user.UserInternalService;
import com.project.soa.audit.AuditLogService;
import com.project.soa.booking.BookingRepository;
import com.project.soa.common.exception.BusinessRuleException;
import com.project.soa.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {

    @Mock HotelRepository hotelRepository;
    @Mock RoomTypeRepository roomTypeRepository;
    @Mock RoomRepository roomRepository;
    @Mock BookingRepository bookingRepository;
    @Mock UserInternalService userService;
    @Mock AuditLogService auditLogService;

    @InjectMocks CatalogServiceImpl service;

    UUID managerId;
    UUID hotelId;
    User manager;
    Hotel hotel;

    @BeforeEach
    void setUp() {
        managerId = UUID.randomUUID();
        hotelId   = UUID.randomUUID();

        manager = new User();
        manager.setId(managerId);
        manager.setName("Alice Manager");

        hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setName("Grand Hotel");
        hotel.setCity("Ramallah");
        hotel.setCountry("PS");
        hotel.setStatus(CatalogStatus.ACTIVE);
        hotel.setManager(manager);
    }

    // ── createHotel ───────────────────────────────────────────────────────────

    @Test
    void createHotel_savesAndReturnsHotel() {
        HotelRequestDto dto = requestDto("Grand Hotel", "Ramallah", "PS");
        when(userService.getById(managerId)).thenReturn(manager);
        when(hotelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Hotel result = service.createHotel(dto, managerId);

        assertThat(result.getName()).isEqualTo("Grand Hotel");
        assertThat(result.getManager()).isEqualTo(manager);
        verify(hotelRepository).save(any(Hotel.class));
    }

    // ── updateHotel ───────────────────────────────────────────────────────────

    @Test
    void updateHotel_byOwner_updatesFields() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(hotelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HotelRequestDto dto = requestDto("Updated Name", "Nablus", "PS");
        Hotel result = service.updateHotel(hotelId, dto, managerId);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getCity()).isEqualTo("Nablus");
    }

    @Test
    void updateHotel_byNonOwner_throwsBusinessRule() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.updateHotel(hotelId, requestDto("X", "Y", "Z"), UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not own");
    }

    @Test
    void updateHotel_notFound_throwsResourceNotFound() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateHotel(hotelId, requestDto("X", "Y", "Z"), managerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteHotel ───────────────────────────────────────────────────────────

    @Test
    void deleteHotel_noActiveBookings_deletes() {
        RoomType rt = roomType(hotel);
        hotel.setRoomTypes(List.of(rt));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(bookingRepository.countActiveOrUpcomingNotCancelledForRoomType(rt.getId(), LocalDate.now()))
                .thenReturn(0L);

        service.deleteHotel(hotelId, managerId);

        verify(hotelRepository).delete(hotel);
    }

    @Test
    void deleteHotel_withActiveBookings_throws() {
        RoomType rt = roomType(hotel);
        hotel.setRoomTypes(List.of(rt));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(bookingRepository.countActiveOrUpcomingNotCancelledForRoomType(any(), any()))
                .thenReturn(2L);

        assertThatThrownBy(() -> service.deleteHotel(hotelId, managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active or upcoming bookings");
    }

    // ── browseActiveHotels ────────────────────────────────────────────────────

    @Test
    void browseActiveHotels_defaultParams_returnsPaginatedList() {
        Page<Hotel> page = new PageImpl<>(List.of(hotel));
        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        HotelListResponseDto result = service.browseActiveHotels(
                0, 20,
                null, null, null,
                null,
                null, null,
                null, null,
                null,
                "name", "asc");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void browseActiveHotels_clampsSizeToHundred() {
        Page<Hotel> page = new PageImpl<>(List.of());
        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        assertThatCode(() -> service.browseActiveHotels(
                0, 500,
                null, null, null,
                null,
                null, null,
                null, null,
                null,
                "name", "asc"))
                .doesNotThrowAnyException();
    }

    @Test
    void browseActiveHotels_unknownSortField_fallsBackToName() {
        Page<Hotel> page = new PageImpl<>(List.of());
        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        assertThatCode(() -> service.browseActiveHotels(
                0, 20,
                null, null, null,
                null,
                null, null,
                null, null,
                null,
                "INVALID_FIELD", "asc"))
                .doesNotThrowAnyException();
    }

    // ── getActiveHotelDetails ─────────────────────────────────────────────────

    @Test
    void getActiveHotelDetails_activeHotel_returnsDto() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        HotelResponseDto dto = service.getActiveHotelDetails(hotelId);

        assertThat(dto.getId()).isEqualTo(hotelId);
        assertThat(dto.getName()).isEqualTo("Grand Hotel");
    }

    @Test
    void getActiveHotelDetails_inactiveHotel_throwsNotFound() {
        hotel.setStatus(CatalogStatus.INACTIVE);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.getActiveHotelDetails(hotelId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActiveHotelDetails_missingHotel_throwsNotFound() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveHotelDetails(hotelId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getRoomTypesForActiveHotel ────────────────────────────────────────────

    @Test
    void getRoomTypesForActiveHotel_returnsOnlyActiveRoomTypes() {
        RoomType active   = roomType(hotel);
        RoomType inactive = roomType(hotel);
        inactive.setStatus(CatalogStatus.INACTIVE);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelId(hotelId)).thenReturn(List.of(active, inactive));

        List<RoomTypeResponseDto> result = service.getRoomTypesForActiveHotel(hotelId);

        assertThat(result).hasSize(1);
    }

    @Test
    void getRoomTypesForActiveHotel_inactiveHotel_throwsNotFound() {
        hotel.setStatus(CatalogStatus.INACTIVE);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.getRoomTypesForActiveHotel(hotelId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── createRoomType ────────────────────────────────────────────────────────

    @Test
    void createRoomType_success() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoomTypeRequestDto dto = roomTypeDto("Suite", new BigDecimal("200.00"), 2, 10);
        RoomType result = service.createRoomType(hotelId, dto, managerId);

        assertThat(result.getName()).isEqualTo("Suite");
        assertThat(result.getHotel()).isEqualTo(hotel);
    }

    // ── deleteRoomType ────────────────────────────────────────────────────────

    @Test
    void createRoom_ownedHotel_createsRoom() {
        RoomType rt = roomType(hotel);
        RoomRequestDto dto = roomDto("101", RoomStatus.AVAILABLE);
        when(roomTypeRepository.findById(rt.getId())).thenReturn(Optional.of(rt));
        when(roomRepository.existsByHotelIdAndRoomNumber(hotelId, "101")).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Room result = service.createRoom(rt.getId(), dto, managerId, false);

        assertThat(result.getRoomNumber()).isEqualTo("101");
        assertThat(result.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(result.getRoomType()).isEqualTo(rt);
        assertThat(result.getHotel()).isEqualTo(hotel);
    }

    @Test
    void createRoom_otherManagersHotel_throws() {
        RoomType rt = roomType(hotel);
        when(roomTypeRepository.findById(rt.getId())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> service.createRoom(
                rt.getId(), roomDto("101", RoomStatus.AVAILABLE), UUID.randomUUID(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not own");
    }

    @Test
    void createRoom_duplicateRoomNumber_throws() {
        RoomType rt = roomType(hotel);
        when(roomTypeRepository.findById(rt.getId())).thenReturn(Optional.of(rt));
        when(roomRepository.existsByHotelIdAndRoomNumber(hotelId, "101")).thenReturn(true);

        assertThatThrownBy(() -> service.createRoom(
                rt.getId(), roomDto("101", RoomStatus.AVAILABLE), managerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getRoomsForRoomType_ownedHotel_returnsRooms() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        when(roomTypeRepository.findById(rt.getId())).thenReturn(Optional.of(rt));
        when(roomRepository.findByRoomTypeIdOrderByRoomNumberAsc(rt.getId())).thenReturn(List.of(room));

        List<RoomResponseDto> result = service.getRoomsForRoomType(rt.getId(), managerId, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoomNumber()).isEqualTo("101");
    }

    @Test
    void updateRoom_duplicateRoomNumber_throws() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        UpdateRoomRequestDto dto = updateRoomDto("102", null);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomRepository.existsByHotelIdAndRoomNumberAndIdNot(hotelId, "102", room.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateRoom(room.getId(), dto, managerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteRoom_occupiedRoom_throws() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        room.setStatus(RoomStatus.OCCUPIED);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.deleteRoom(room.getId(), managerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot delete room");
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void deleteRoom_checkedInAssignment_throws() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(bookingRepository.existsByRoomIdAndStatus(room.getId(), com.project.soa.booking.BookingStatus.CHECKED_IN))
                .thenReturn(true);

        assertThatThrownBy(() -> service.deleteRoom(room.getId(), managerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot delete room");
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void deleteRoom_availableRoom_deletes() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(bookingRepository.existsByRoomIdAndStatus(room.getId(), com.project.soa.booking.BookingStatus.CHECKED_IN))
                .thenReturn(false);

        service.deleteRoom(room.getId(), managerId, false);

        verify(roomRepository).delete(room);
    }

    @Test
    void deleteRoomType_withBookings_throws() {
        RoomType rt = roomType(hotel);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(rt.getId())).thenReturn(Optional.of(rt));
        when(bookingRepository.countActiveOrUpcomingNotCancelledForRoomType(rt.getId(), LocalDate.now()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.deleteRoomType(hotelId, rt.getId(), managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active or upcoming booking");
    }

    @Test
    void deleteRoomType_wrongHotel_throws() {
        RoomType rt = roomType(hotel);
        Hotel other = new Hotel();
        other.setId(UUID.randomUUID());
        rt.setHotel(other);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(rt.getId())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> service.deleteRoomType(hotelId, rt.getId(), managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong");
    }

    // ── CatalogInternalService ────────────────────────────────────────────────

    @Test
    void getRoomTypeForUpdate_found_returnsRoomType() {
        RoomType rt = roomType(hotel);
        when(roomTypeRepository.findByIdForUpdate(rt.getId())).thenReturn(Optional.of(rt));

        RoomType result = service.getRoomTypeForUpdate(rt.getId());

        assertThat(result).isEqualTo(rt);
    }

    @Test
    void getRoomTypeForUpdate_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(roomTypeRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoomTypeForUpdate(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignAvailableRoom_availableRoom_marksOccupied() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        LocalDate checkIn = LocalDate.of(2025, 7, 1);
        LocalDate checkOut = LocalDate.of(2025, 7, 5);
        when(roomRepository.findAvailableRoomsForAssignment(rt.getId(), checkIn, checkOut))
                .thenReturn(List.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Room result = service.assignAvailableRoom(rt.getId(), checkIn, checkOut);

        assertThat(result).isEqualTo(room);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OCCUPIED);
        verify(roomRepository).save(room);
    }

    @Test
    void assignAvailableRoom_noAvailableRoom_throws() {
        UUID roomTypeId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2025, 7, 1);
        LocalDate checkOut = LocalDate.of(2025, 7, 5);
        when(roomRepository.findAvailableRoomsForAssignment(roomTypeId, checkIn, checkOut))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assignAvailableRoom(roomTypeId, checkIn, checkOut))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No available rooms");
    }

    @Test
    void releaseRoom_occupiedRoom_marksAvailable() {
        RoomType rt = roomType(hotel);
        Room room = room("101", rt);
        room.setStatus(RoomStatus.OCCUPIED);
        when(roomRepository.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.releaseRoom(room.getId());

        assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        verify(roomRepository).save(room);
    }

    @Test
    void findHotelById_delegatesToRepository() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThat(service.findHotelById(hotelId)).contains(hotel);
    }

    @Test
    void countHotels_delegatesToRepository() {
        when(hotelRepository.count()).thenReturn(42L);

        assertThat(service.countHotels()).isEqualTo(42L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HotelRequestDto requestDto(String name, String city, String country) {
        HotelRequestDto dto = new HotelRequestDto();
        dto.setName(name);
        dto.setCity(city);
        dto.setCountry(country);
        return dto;
    }

    private RoomType roomType(Hotel h) {
        RoomType rt = new RoomType();
        rt.setId(UUID.randomUUID());
        rt.setName("Standard");
        rt.setBasePrice(new BigDecimal("100.00"));
        rt.setCapacity(2);
        rt.setTotalRooms(5);
        rt.setStatus(CatalogStatus.ACTIVE);
        rt.setHotel(h);
        return rt;
    }

    private Room room(String roomNumber, RoomType roomType) {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setRoomNumber(roomNumber);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setRoomType(roomType);
        room.setHotel(roomType.getHotel());
        return room;
    }

    private RoomRequestDto roomDto(String roomNumber, RoomStatus status) {
        RoomRequestDto dto = new RoomRequestDto();
        dto.setRoomNumber(roomNumber);
        dto.setStatus(status);
        return dto;
    }

    private UpdateRoomRequestDto updateRoomDto(String roomNumber, RoomStatus status) {
        UpdateRoomRequestDto dto = new UpdateRoomRequestDto();
        dto.setRoomNumber(roomNumber);
        dto.setStatus(status);
        return dto;
    }

    private RoomTypeRequestDto roomTypeDto(String name, BigDecimal price, int capacity, int totalRooms) {
        RoomTypeRequestDto dto = new RoomTypeRequestDto();
        dto.setName(name);
        dto.setBasePrice(price);
        dto.setCapacity(capacity);
        dto.setTotalRooms(totalRooms);
        return dto;
    }
}
