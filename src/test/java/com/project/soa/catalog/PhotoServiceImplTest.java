package com.project.soa.catalog;

import com.project.soa.auth.user.User;
import com.project.soa.audit.AuditLogService;
import com.project.soa.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceImplTest {

    @Mock PhotoRepository photoRepository;
    @Mock HotelRepository hotelRepository;
    @Mock RoomTypeRepository roomTypeRepository;
    @Mock PhotoStorageService photoStorageService;
    @Mock AuditLogService auditLogService;

    PhotoServiceImpl service;
    UUID hotelId;
    UUID managerId;
    UUID otherManagerId;
    Hotel hotel;

    @BeforeEach
    void setUp() {
        service = new PhotoServiceImpl(
                photoRepository, hotelRepository, roomTypeRepository, photoStorageService, auditLogService);
        hotelId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        otherManagerId = UUID.randomUUID();
        hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setManager(user(managerId));
    }

    @Test
    void createHotelPhoto_upload_storesFileReferenceOnPhoto() {
        UploadPhotoRequestDto dto = uploadDto();
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(photoStorageService.store(dto.getFile())).thenReturn("/uploads/photos/photo.png");
        when(photoRepository.findMaxDisplayOrderByHotelId(hotelId)).thenReturn(2);
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        PhotoResponseDto result = service.createHotelPhoto(hotelId, dto, managerId, false);

        assertThat(result.getUrl()).isEqualTo("/uploads/photos/photo.png");
        assertThat(result.getCaption()).isEqualTo("Lobby");
        assertThat(result.getType()).isEqualTo(PhotoType.HOTEL);
        assertThat(result.getDisplayOrder()).isEqualTo(3);
        assertThat(result.getHotelId()).isEqualTo(hotelId);
        verify(photoStorageService).store(dto.getFile());
    }

    @Test
    void createHotelPhoto_url_ownedHotel_succeeds() {
        CreatePhotoRequestDto dto = createDto("https://example.com/lobby.jpg", PhotoType.HOTEL);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        PhotoResponseDto result = service.createHotelPhoto(hotelId, dto, managerId, false);

        assertThat(result.getUrl()).isEqualTo(dto.getUrl());
        assertThat(result.getHotelId()).isEqualTo(hotelId);
    }

    @Test
    void createHotelPhoto_url_otherManagersHotel_throws() {
        CreatePhotoRequestDto dto = createDto("https://example.com/lobby.jpg", PhotoType.HOTEL);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createHotelPhoto(hotelId, dto, otherManagerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Access denied");

        verify(photoRepository, never()).save(any());
    }

    @Test
    void createHotelPhoto_upload_otherManagersHotel_throwsBeforeStorage() {
        UploadPhotoRequestDto dto = uploadDto();
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createHotelPhoto(hotelId, dto, otherManagerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Access denied");

        verify(photoStorageService, never()).store(any());
        verify(photoRepository, never()).save(any());
    }

    @Test
    void createHotelPhoto_adminBypassesOwnership() {
        CreatePhotoRequestDto dto = createDto("https://example.com/lobby.jpg", PhotoType.HOTEL);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        PhotoResponseDto result = service.createHotelPhoto(hotelId, dto, otherManagerId, true);

        assertThat(result.getUrl()).isEqualTo(dto.getUrl());
        assertThat(result.getHotelId()).isEqualTo(hotelId);
    }

    @Test
    void createRoomTypePhoto_ownedHotel_succeeds() {
        RoomType roomType = roomType();
        CreatePhotoRequestDto dto = createDto("https://example.com/room.jpg", PhotoType.ROOM_TYPE);
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        PhotoResponseDto result = service.createRoomTypePhoto(roomType.getId(), dto, managerId, false);

        assertThat(result.getUrl()).isEqualTo(dto.getUrl());
        assertThat(result.getRoomTypeId()).isEqualTo(roomType.getId());
    }

    @Test
    void createRoomTypePhoto_otherManagersHotel_throws() {
        RoomType roomType = roomType();
        CreatePhotoRequestDto dto = createDto("https://example.com/room.jpg", PhotoType.ROOM_TYPE);
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));

        assertThatThrownBy(() -> service.createRoomTypePhoto(roomType.getId(), dto, otherManagerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Access denied");

        verify(photoRepository, never()).save(any());
    }

    @Test
    void createRoomTypePhoto_upload_otherManagersHotel_throwsBeforeStorage() {
        RoomType roomType = roomType();
        UploadPhotoRequestDto dto = uploadDto();
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));

        assertThatThrownBy(() -> service.createRoomTypePhoto(roomType.getId(), dto, otherManagerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Access denied");

        verify(photoStorageService, never()).store(any());
        verify(photoRepository, never()).save(any());
    }

    @Test
    void createRoomTypePhoto_adminBypassesOwnership() {
        RoomType roomType = roomType();
        CreatePhotoRequestDto dto = createDto("https://example.com/room.jpg", PhotoType.ROOM_TYPE);
        when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        PhotoResponseDto result = service.createRoomTypePhoto(roomType.getId(), dto, otherManagerId, true);

        assertThat(result.getUrl()).isEqualTo(dto.getUrl());
        assertThat(result.getRoomTypeId()).isEqualTo(roomType.getId());
    }

    @Test
    void deletePhoto_ownedHotel_succeeds() {
        UUID photoId = UUID.randomUUID();
        Photo photo = hotelPhoto(photoId);
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        service.deletePhoto(photoId, managerId, false);

        verify(photoRepository).delete(photo);
    }

    @Test
    void deletePhoto_otherManagersHotel_throws() {
        UUID photoId = UUID.randomUUID();
        Photo photo = hotelPhoto(photoId);
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> service.deletePhoto(photoId, otherManagerId, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Access denied");

        verify(photoRepository, never()).delete(any());
    }

    @Test
    void deletePhoto_adminBypassesOwnership() {
        UUID photoId = UUID.randomUUID();
        Photo photo = hotelPhoto(photoId);
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        service.deletePhoto(photoId, otherManagerId, true);

        verify(photoRepository).delete(photo);
    }

    private CreatePhotoRequestDto createDto(String url, PhotoType type) {
        CreatePhotoRequestDto dto = new CreatePhotoRequestDto();
        dto.setUrl(url);
        dto.setCaption("Lobby");
        dto.setType(type);
        dto.setIsActive(true);
        return dto;
    }

    private UploadPhotoRequestDto uploadDto() {
        UploadPhotoRequestDto dto = new UploadPhotoRequestDto();
        dto.setFile(new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3}));
        dto.setCaption("Lobby");
        dto.setType(PhotoType.HOTEL);
        dto.setIsActive(true);
        return dto;
    }

    private RoomType roomType() {
        RoomType roomType = new RoomType();
        roomType.setId(UUID.randomUUID());
        roomType.setHotel(hotel);
        return roomType;
    }

    private Photo hotelPhoto(UUID photoId) {
        Photo photo = new Photo();
        photo.setId(photoId);
        photo.setHotel(hotel);
        photo.setUrl("https://example.com/lobby.jpg");
        photo.setType(PhotoType.HOTEL);
        return photo;
    }

    private User user(UUID userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
