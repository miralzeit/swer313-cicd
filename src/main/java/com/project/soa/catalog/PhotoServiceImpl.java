package com.project.soa.catalog;

import com.project.soa.audit.AuditLogService;
import com.project.soa.common.exception.BusinessRuleException;
import com.project.soa.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PhotoServiceImpl implements PhotoService {

    private final PhotoRepository photoRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final PhotoStorageService photoStorageService;
    private final AuditLogService auditLogService;

    public PhotoServiceImpl(PhotoRepository photoRepository,
                            HotelRepository hotelRepository,
                            RoomTypeRepository roomTypeRepository,
                            PhotoStorageService photoStorageService,
                            AuditLogService auditLogService) {
        this.photoRepository    = photoRepository;
        this.hotelRepository    = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.photoStorageService = photoStorageService;
        this.auditLogService = auditLogService;
    }

    @Override
    public PhotoResponseDto createHotelPhoto(UUID hotelId, CreatePhotoRequestDto dto, UUID callerId, boolean admin) {
        Hotel hotel = requireManagedHotel(hotelId, callerId, admin);
        validatePhotoUrl(hotelId, null, dto.getUrl());
        Photo photo = PhotoMapper.toEntity(dto, hotel, null);
        Integer maxOrder = photoRepository.findMaxDisplayOrderByHotelId(hotelId);
        photo.setDisplayOrder(maxOrder != null ? maxOrder + 1 : 0);
        Photo saved = photoRepository.save(photo);
        auditLogService.logCurrentActor("PHOTO_ADDED", "Photo", saved.getId(), "Hotel photo URL added");
        return PhotoMapper.toDto(saved);
    }

    @Override
    public PhotoResponseDto createHotelPhoto(UUID hotelId, UploadPhotoRequestDto dto, UUID callerId, boolean admin) {
        Hotel hotel = requireManagedHotel(hotelId, callerId, admin);
        String storedPhotoUrl = photoStorageService.store(dto.getFile());
        Photo photo = createUploadedPhoto(storedPhotoUrl, dto, hotel, null);
        Integer maxOrder = photoRepository.findMaxDisplayOrderByHotelId(hotelId);
        photo.setDisplayOrder(maxOrder != null ? maxOrder + 1 : 0);
        Photo saved = photoRepository.save(photo);
        auditLogService.logCurrentActor("PHOTO_UPLOADED", "Photo", saved.getId(), "Hotel photo uploaded");
        return PhotoMapper.toDto(saved);
    }

    @Override
    public PhotoResponseDto createRoomTypePhoto(UUID roomTypeId, CreatePhotoRequestDto dto, UUID callerId, boolean admin) {
        RoomType roomType = requireManagedRoomType(roomTypeId, callerId, admin);
        validatePhotoUrl(null, roomTypeId, dto.getUrl());
        Photo photo = PhotoMapper.toEntity(dto, null, roomType);
        Integer maxOrder = photoRepository.findMaxDisplayOrderByRoomTypeId(roomTypeId);
        photo.setDisplayOrder(maxOrder != null ? maxOrder + 1 : 0);
        Photo saved = photoRepository.save(photo);
        auditLogService.logCurrentActor("PHOTO_ADDED", "Photo", saved.getId(), "Room type photo URL added");
        return PhotoMapper.toDto(saved);
    }

    @Override
    public PhotoResponseDto createRoomTypePhoto(UUID roomTypeId, UploadPhotoRequestDto dto, UUID callerId, boolean admin) {
        RoomType roomType = requireManagedRoomType(roomTypeId, callerId, admin);
        String storedPhotoUrl = photoStorageService.store(dto.getFile());
        Photo photo = createUploadedPhoto(storedPhotoUrl, dto, null, roomType);
        Integer maxOrder = photoRepository.findMaxDisplayOrderByRoomTypeId(roomTypeId);
        photo.setDisplayOrder(maxOrder != null ? maxOrder + 1 : 0);
        Photo saved = photoRepository.save(photo);
        auditLogService.logCurrentActor("PHOTO_UPLOADED", "Photo", saved.getId(), "Room type photo uploaded");
        return PhotoMapper.toDto(saved);
    }

    @Override
    public PhotoResponseDto updatePhoto(UUID photoId, CreatePhotoRequestDto dto, UUID callerId, boolean admin) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        requireManagedPhoto(photo, callerId, admin);
        if (!dto.getUrl().equals(photo.getUrl())) {
            UUID hotelId    = photo.getHotel()    != null ? photo.getHotel().getId()    : null;
            UUID roomTypeId = photo.getRoomType() != null ? photo.getRoomType().getId() : null;
            validatePhotoUrl(hotelId, roomTypeId, dto.getUrl());
        }
        PhotoMapper.updateEntity(photo, dto);
        return PhotoMapper.toDto(photoRepository.save(photo));
    }

    @Override
    public void deletePhoto(UUID photoId, UUID callerId, boolean admin) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        requireManagedPhoto(photo, callerId, admin);
        photoRepository.delete(photo);
        auditLogService.logCurrentActor("PHOTO_DELETED", "Photo", photoId, "Photo deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoResponseDto> getHotelPhotos(UUID hotelId) {
        return photoRepository.findByHotelIdOrderByDisplayOrderAsc(hotelId)
                .stream().map(PhotoMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoResponseDto> getHotelPhotosByType(UUID hotelId, PhotoType type) {
        return photoRepository.findByHotelIdAndTypeOrderByDisplayOrderAsc(hotelId, type)
                .stream().map(PhotoMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoResponseDto> getActiveHotelPhotos(UUID hotelId) {
        return photoRepository.findByHotelIdAndIsActiveOrderByDisplayOrderAsc(hotelId, true)
                .stream().map(PhotoMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoResponseDto> getRoomTypePhotos(UUID roomTypeId) {
        return photoRepository.findByRoomTypeIdOrderByDisplayOrderAsc(roomTypeId)
                .stream().map(PhotoMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoResponseDto> getActiveRoomTypePhotos(UUID roomTypeId) {
        return photoRepository.findByRoomTypeIdAndIsActiveOrderByDisplayOrderAsc(roomTypeId, true)
                .stream().map(PhotoMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PhotoResponseDto getPhoto(UUID photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        return PhotoMapper.toDto(photo);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validatePhotoUrl(UUID hotelId, UUID roomTypeId, String url) {
        if (hotelId != null && photoRepository.existsByHotelIdAndUrl(hotelId, url)) {
            throw new BusinessRuleException("Photo URL already exists for this hotel.");
        }
        if (roomTypeId != null && photoRepository.existsByRoomTypeIdAndUrl(roomTypeId, url)) {
            throw new BusinessRuleException("Photo URL already exists for this room type.");
        }
    }

    private Hotel requireManagedHotel(UUID hotelId, UUID callerId, boolean admin) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelId));
        if (!admin) {
            requireOwnedHotel(hotel, callerId);
        }
        return hotel;
    }

    private RoomType requireManagedRoomType(UUID roomTypeId, UUID callerId, boolean admin) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", roomTypeId));
        if (!admin) {
            requireOwnedHotel(roomType.getHotel(), callerId);
        }
        return roomType;
    }

    private void requireManagedPhoto(Photo photo, UUID callerId, boolean admin) {
        if (admin) {
            return;
        }
        if (photo.getHotel() != null) {
            requireOwnedHotel(photo.getHotel(), callerId);
            return;
        }
        if (photo.getRoomType() != null) {
            requireOwnedHotel(photo.getRoomType().getHotel(), callerId);
            return;
        }
        throw new BusinessRuleException("Access denied: photo is not attached to a managed hotel.");
    }

    private void requireOwnedHotel(Hotel hotel, UUID callerId) {
        if (hotel == null || hotel.getManager() == null || !hotel.getManager().getId().equals(callerId)) {
            throw new BusinessRuleException("Access denied: you do not own this hotel.");
        }
    }

    private Photo createUploadedPhoto(String storedPhotoUrl,
                                      UploadPhotoRequestDto dto,
                                      Hotel hotel,
                                      RoomType roomType) {
        Photo photo = new Photo();
        photo.setUrl(storedPhotoUrl);
        photo.setCaption(dto.getCaption());
        photo.setType(dto.getType());
        photo.setDisplayOrder(dto.getDisplayOrder());
        photo.setIsActive(dto.getIsActive());
        photo.setHotel(hotel);
        photo.setRoomType(roomType);
        return photo;
    }
}
