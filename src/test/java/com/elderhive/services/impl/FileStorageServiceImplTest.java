package com.elderhive.services.impl;

import com.elderhive.config.CloudinaryConfig;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.services.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Exercises only the local-disk path (CloudinaryConfig#isConfigured() mocked false) -
 * Cloudinary itself isn't reachable/configured in a unit test, and its branch is a thin
 * pass-through to the SDK anyway. Uses a real temp directory, not a mock filesystem, since
 * the whole point is to verify actual bytes land on disk and come back out again.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceImplTest {

    @Mock
    private CloudinaryConfig cloudinaryConfig;

    private FileStorageServiceImpl fileStorageService;
    private Path tempUploadDir;

    @BeforeEach
    void setUp() throws IOException {
        tempUploadDir = Files.createTempDirectory("elderhive-filestorage-test");
        fileStorageService = new FileStorageServiceImpl(cloudinaryConfig);
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempUploadDir.toString());
        ReflectionTestUtils.setField(fileStorageService, "publicBaseUrl", "http://localhost:8080/uploads");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempUploadDir)) {
            Files.walk(tempUploadDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void storingAFileWritesItToLocalDiskUnderAYearMonthSubfolderAndReturnsAResolvableUrl() throws GenericException, IOException {
        when(cloudinaryConfig.isConfigured()).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "id-card.pdf", "application/pdf", "hello-bytes".getBytes());

        FileStorageService.StoredFile stored = fileStorageService.store(file, "caretaker-verification-documents");

        String expectedSubDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        assertThat(stored.storageKey()).startsWith(expectedSubDir + "/");
        assertThat(stored.storageKey()).endsWith(".pdf");
        assertThat(stored.url()).isEqualTo("http://localhost:8080/uploads/" + stored.storageKey());
        assertThat(Files.readString(tempUploadDir.resolve(stored.storageKey()))).isEqualTo("hello-bytes");
    }

    @Test
    void resolveUrlReconstructsTheSameUrlFromTheStorageKeyAloneForALocalDiskFile() {
        String url = fileStorageService.resolveUrl("2026/10/some-uuid.pdf");
        assertThat(url).isEqualTo("http://localhost:8080/uploads/2026/10/some-uuid.pdf");
    }

    @Test
    void resolveUrlReconstructsACloudinaryDeliveryUrlFromACloudinaryStorageKey() {
        when(cloudinaryConfig.getCloudName()).thenReturn("demo-cloud");
        String url = fileStorageService.resolveUrl("cloudinary:raw:elderhive/caretaker-verification-documents/id_abc123");

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo-cloud/raw/upload/elderhive/caretaker-verification-documents/id_abc123");
    }

    @Test
    void deletingALocalDiskFileRemovesItFromDisk() throws GenericException {
        when(cloudinaryConfig.isConfigured()).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());
        FileStorageService.StoredFile stored = fileStorageService.store(file, "caretaker-verification-documents");
        assertThat(Files.exists(tempUploadDir.resolve(stored.storageKey()))).isTrue();

        fileStorageService.delete(stored.storageKey());

        assertThat(Files.exists(tempUploadDir.resolve(stored.storageKey()))).isFalse();
    }

    @Test
    void deletingACloudinaryKeyNeverTouchesLocalDiskAndDoesNotThrowEvenIfCloudinaryCallFails() {
        when(cloudinaryConfig.getCloudinary()).thenThrow(new RuntimeException("not reachable in a unit test"));
        fileStorageService.delete("cloudinary:raw:elderhive/caretaker-verification-documents/id_abc123");
    }

    @Test
    void storingAnEmptyFileThrowsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.store(empty, "caretaker-verification-documents"))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BAD_REQUEST);
    }
}
