package com.project.soa.catalog;

import com.project.soa.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void store_validImage_writesFileAndReturnsPublicPath() throws Exception {
        PhotoStorageServiceImpl service = new PhotoStorageServiceImpl(tempDir.toString(), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "hotel.png", "image/png", new byte[] {1, 2, 3});

        String result = service.store(file);

        assertThat(result).startsWith("/uploads/photos/");
        assertThat(result).endsWith(".png");
        String filename = result.substring("/uploads/photos/".length());
        assertThat(Files.readAllBytes(tempDir.resolve(filename))).containsExactly(1, 2, 3);
    }

    @Test
    void store_unsupportedContentType_throwsBusinessRule() {
        PhotoStorageServiceImpl service = new PhotoStorageServiceImpl(tempDir.toString(), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "hotel.txt", "text/plain", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only JPEG, PNG, WEBP, and GIF");
    }

    @Test
    void store_missingFile_throwsBusinessRule() {
        PhotoStorageServiceImpl service = new PhotoStorageServiceImpl(tempDir.toString(), 1024);

        assertThatThrownBy(() -> service.store(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Photo file is required");
    }

    @Test
    void store_emptyFile_throwsBusinessRule() {
        PhotoStorageServiceImpl service = new PhotoStorageServiceImpl(tempDir.toString(), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "hotel.png", "image/png", new byte[] {});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Photo file is required");
    }

    @Test
    void store_fileTooLarge_throwsBusinessRule() {
        PhotoStorageServiceImpl service = new PhotoStorageServiceImpl(tempDir.toString(), 2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "hotel.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum allowed size");
    }
}
