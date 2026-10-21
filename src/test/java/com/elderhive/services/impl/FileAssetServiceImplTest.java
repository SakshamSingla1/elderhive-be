package com.elderhive.services.impl;

import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.entities.FileAsset;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.FileAssetRepository;
import com.elderhive.services.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAssetServiceImplTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileAssetRepository fileAssetRepository;

    @InjectMocks
    private FileAssetServiceImpl fileAssetService;

    @Test
    void uploadingAnAllowedMimeTypeStoresTheFileAndPersistsAFileAssetRow() throws GenericException {
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());
        when(fileStorageService.store(file, "caretaker-verification-documents"))
                .thenReturn(new FileStorageService.StoredFile("2026/10/uuid.pdf", "http://x/2026/10/uuid.pdf"));
        when(fileStorageService.resolveUrl("2026/10/uuid.pdf")).thenReturn("http://x/2026/10/uuid.pdf");
        when(fileAssetRepository.save(any())).thenAnswer(inv -> {
            FileAsset asset = inv.getArgument(0);
            asset.setId(1L);
            return asset;
        });

        FileAssetResponse response = fileAssetService.upload(file, "caretaker-verification-documents", 10L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUrl()).isEqualTo("http://x/2026/10/uuid.pdf");
        assertThat(response.getOriginalFilename()).isEqualTo("id.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSize()).isEqualTo(5L);
        assertThat(response.getUploadedByUserId()).isEqualTo(10L);
    }

    @Test
    void uploadingADisallowedMimeTypeThrowsAndNeverCallsStore() throws GenericException {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/x-msdownload", "bytes".getBytes());

        assertThatThrownBy(() -> fileAssetService.upload(file, "caretaker-verification-documents", 10L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FILE_TYPE_NOT_ALLOWED);

        verify(fileStorageService, never()).store(any(), any());
    }

    @Test
    void imagesAndVideosAreAlsoAllowed() throws GenericException {
        when(fileStorageService.store(any(), any())).thenReturn(new FileStorageService.StoredFile("key", "url"));
        when(fileAssetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile image = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        MockMultipartFile video = new MockMultipartFile("file", "clip.mp4", "video/mp4", "bytes".getBytes());

        fileAssetService.upload(image, "folder", 1L);
        fileAssetService.upload(video, "folder", 1L);
    }

    @Test
    void getByIdReturnsTheResolvedResponse() throws GenericException {
        FileAsset asset = FileAsset.builder().id(1L).storageKey("key").originalFilename("x.pdf").build();
        when(fileAssetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(fileStorageService.resolveUrl("key")).thenReturn("http://x/key");

        FileAssetResponse response = fileAssetService.getById(1L);

        assertThat(response.getUrl()).isEqualTo("http://x/key");
    }

    @Test
    void getByIdThrowsFileNotFoundForAnUnknownId() {
        when(fileAssetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileAssetService.getById(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FILE_NOT_FOUND);
    }

    @Test
    void deleteRemovesTheStoredFileThenTheRow() throws GenericException {
        FileAsset asset = FileAsset.builder().id(1L).storageKey("key").build();
        when(fileAssetRepository.findById(1L)).thenReturn(Optional.of(asset));

        fileAssetService.delete(1L);

        verify(fileStorageService).delete("key");
        verify(fileAssetRepository).deleteById(1L);
    }

    @Test
    void deletingAnUnknownIdThrowsFileNotFound() {
        when(fileAssetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileAssetService.delete(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FILE_NOT_FOUND);
    }
}
