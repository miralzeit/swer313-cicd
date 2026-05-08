package com.project.soa.catalog;

public final class RoomMapper {

    private RoomMapper() {}

    public static RoomResponseDto toDto(Room room) {
        RoomResponseDto dto = new RoomResponseDto();
        dto.setId(room.getId());
        dto.setRoomNumber(room.getRoomNumber());
        dto.setStatus(room.getStatus() != null ? room.getStatus().name() : null);
        dto.setRoomTypeId(room.getRoomType() != null ? room.getRoomType().getId() : null);
        dto.setHotelId(room.getHotel() != null ? room.getHotel().getId() : null);
        return dto;
    }
}
