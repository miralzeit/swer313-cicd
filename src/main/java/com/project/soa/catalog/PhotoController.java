package com.project.soa.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@Tag(name = "Photo Management", description = "APIs for managing hotel and room type photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/hotels/{hotelId}")
    @Operation(summary = "Add a photo to a hotel")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<PhotoResponseDto> createHotelPhoto(
            @Parameter(description = "Hotel ID") @PathVariable UUID hotelId,
            @Valid @RequestBody CreatePhotoRequestDto dto,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(photoService.createHotelPhoto(hotelId, dto, userId(jwt), isAdmin(authentication)));
    }

    @PostMapping(value = "/hotels/{hotelId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo file to a hotel")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<PhotoResponseDto> uploadHotelPhoto(
            @Parameter(description = "Hotel ID") @PathVariable UUID hotelId,
            @Valid @ModelAttribute UploadPhotoRequestDto dto,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(photoService.createHotelPhoto(hotelId, dto, userId(jwt), isAdmin(authentication)));
    }

    @PostMapping("/room-types/{roomTypeId}")
    @Operation(summary = "Add a photo to a room type")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<PhotoResponseDto> createRoomTypePhoto(
            @Parameter(description = "Room Type ID") @PathVariable UUID roomTypeId,
            @Valid @RequestBody CreatePhotoRequestDto dto,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(photoService.createRoomTypePhoto(roomTypeId, dto, userId(jwt), isAdmin(authentication)));
    }

    @PostMapping(value = "/room-types/{roomTypeId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo file to a room type")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<PhotoResponseDto> uploadRoomTypePhoto(
            @Parameter(description = "Room Type ID") @PathVariable UUID roomTypeId,
            @Valid @ModelAttribute UploadPhotoRequestDto dto,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(photoService.createRoomTypePhoto(roomTypeId, dto, userId(jwt), isAdmin(authentication)));
    }

    @PutMapping("/{photoId}")
    @Operation(summary = "Update a photo")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<PhotoResponseDto> updatePhoto(
            @Parameter(description = "Photo ID") @PathVariable UUID photoId,
            @Valid @RequestBody CreatePhotoRequestDto dto,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return ResponseEntity.ok(photoService.updatePhoto(photoId, dto, userId(jwt), isAdmin(authentication)));
    }

    @DeleteMapping("/{photoId}")
    @Operation(summary = "Delete a photo")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deletePhoto(
            @Parameter(description = "Photo ID") @PathVariable UUID photoId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        photoService.deletePhoto(photoId, userId(jwt), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hotels/{hotelId}")
    @Operation(summary = "Get all photos for a hotel")
    public ResponseEntity<List<PhotoResponseDto>> getHotelPhotos(
            @Parameter(description = "Hotel ID") @PathVariable UUID hotelId) {
        return ResponseEntity.ok(photoService.getHotelPhotos(hotelId));
    }

    @GetMapping("/hotels/{hotelId}/type/{type}")
    @Operation(summary = "Get hotel photos filtered by type")
    public ResponseEntity<List<PhotoResponseDto>> getHotelPhotosByType(
            @Parameter(description = "Hotel ID") @PathVariable UUID hotelId,
            @Parameter(description = "Photo type") @PathVariable PhotoType type) {
        return ResponseEntity.ok(photoService.getHotelPhotosByType(hotelId, type));
    }

    @GetMapping("/hotels/{hotelId}/active")
    @Operation(summary = "Get active photos for a hotel")
    public ResponseEntity<List<PhotoResponseDto>> getActiveHotelPhotos(
            @Parameter(description = "Hotel ID") @PathVariable UUID hotelId) {
        return ResponseEntity.ok(photoService.getActiveHotelPhotos(hotelId));
    }

    @GetMapping("/room-types/{roomTypeId}")
    @Operation(summary = "Get all photos for a room type")
    public ResponseEntity<List<PhotoResponseDto>> getRoomTypePhotos(
            @Parameter(description = "Room Type ID") @PathVariable UUID roomTypeId) {
        return ResponseEntity.ok(photoService.getRoomTypePhotos(roomTypeId));
    }

    @GetMapping("/room-types/{roomTypeId}/active")
    @Operation(summary = "Get active photos for a room type")
    public ResponseEntity<List<PhotoResponseDto>> getActiveRoomTypePhotos(
            @Parameter(description = "Room Type ID") @PathVariable UUID roomTypeId) {
        return ResponseEntity.ok(photoService.getActiveRoomTypePhotos(roomTypeId));
    }

    @GetMapping("/{photoId}")
    @Operation(summary = "Get a single photo by ID")
    public ResponseEntity<PhotoResponseDto> getPhoto(
            @Parameter(description = "Photo ID") @PathVariable UUID photoId) {
        return ResponseEntity.ok(photoService.getPhoto(photoId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
