package com.project.soa.catalog;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public class UploadPhotoRequestDto {

    @NotNull(message = "Photo file is required")
    private MultipartFile file;

    private String caption;

    @NotNull(message = "Photo type is required")
    private PhotoType type;

    private Integer displayOrder = 0;

    private Boolean isActive = true;

    public UploadPhotoRequestDto() {}

    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public PhotoType getType() { return type; }
    public void setType(PhotoType type) { this.type = type; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
