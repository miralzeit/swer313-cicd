package com.project.soa.catalog;

import java.util.List;
import java.util.UUID;

public interface PhotoService {

    PhotoResponseDto createHotelPhoto(UUID hotelId, CreatePhotoRequestDto dto, UUID callerId, boolean admin);

    PhotoResponseDto createHotelPhoto(UUID hotelId, UploadPhotoRequestDto dto, UUID callerId, boolean admin);

    PhotoResponseDto createRoomTypePhoto(UUID roomTypeId, CreatePhotoRequestDto dto, UUID callerId, boolean admin);

    PhotoResponseDto createRoomTypePhoto(UUID roomTypeId, UploadPhotoRequestDto dto, UUID callerId, boolean admin);

    PhotoResponseDto updatePhoto(UUID photoId, CreatePhotoRequestDto dto, UUID callerId, boolean admin);

    void deletePhoto(UUID photoId, UUID callerId, boolean admin);

    List<PhotoResponseDto> getHotelPhotos(UUID hotelId);

    List<PhotoResponseDto> getHotelPhotosByType(UUID hotelId, PhotoType type);

    List<PhotoResponseDto> getActiveHotelPhotos(UUID hotelId);

    List<PhotoResponseDto> getRoomTypePhotos(UUID roomTypeId);

    List<PhotoResponseDto> getActiveRoomTypePhotos(UUID roomTypeId);

    PhotoResponseDto getPhoto(UUID photoId);
}
