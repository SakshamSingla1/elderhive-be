package com.elderhive.services.impl;

import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.entities.FileAsset;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.FileAssetRepository;
import com.elderhive.services.FileAssetService;
import com.elderhive.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/** Ported from ElderSphere's own FileAssetServiceImpl, including its MIME allow-list. */
@Service
@RequiredArgsConstructor
public class FileAssetServiceImpl implements FileAssetService {

    private final FileStorageService fileStorageService;
    private final FileAssetRepository fileAssetRepository;

    private static final Set<String> ALLOWED_DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private static final Set<String> ALLOWED_VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime", "video/ogg",
            "video/x-msvideo", "video/x-ms-wmv", "video/mpeg", "video/3gpp",
            "video/3gpp2", "video/x-flv", "video/x-matroska"
    );

    @Override
    @Transactional
    public FileAssetResponse upload(MultipartFile file, String folder, Long uploadedByUserId) throws GenericException {
        validateMimeType(file != null ? file.getContentType() : null);
        FileStorageService.StoredFile stored = fileStorageService.store(file, folder);
        FileAsset asset = FileAsset.builder()
                .storageKey(stored.storageKey())
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .uploadedByUserId(uploadedByUserId)
                .build();
        return toResponse(fileAssetRepository.save(asset));
    }

    /** Allows images, PDFs, Word/Excel documents, and video - rejects everything else (e.g. raw executables, archives). */
    private void validateMimeType(String mimeType) throws GenericException {
        boolean allowed = mimeType != null
                && (mimeType.startsWith("image/") || ALLOWED_DOCUMENT_MIME_TYPES.contains(mimeType) || ALLOWED_VIDEO_MIME_TYPES.contains(mimeType));
        if (!allowed) {
            throw new GenericException(ExceptionCodeEnum.FILE_TYPE_NOT_ALLOWED,
                    "Unsupported file type: " + mimeType + ". Only images, PDF, Word, Excel, and video files are allowed.");
        }
    }

    @Override
    public FileAssetResponse getById(Long id) throws GenericException {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.FILE_NOT_FOUND, "File not found"));
        return toResponse(asset);
    }

    @Override
    @Transactional
    public void delete(Long id) throws GenericException {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.FILE_NOT_FOUND, "File not found"));
        fileStorageService.delete(asset.getStorageKey());
        fileAssetRepository.deleteById(id);
    }

    private FileAssetResponse toResponse(FileAsset asset) {
        return FileAssetResponse.builder()
                .id(asset.getId())
                .url(fileStorageService.resolveUrl(asset.getStorageKey()))
                .originalFilename(asset.getOriginalFilename())
                .contentType(asset.getContentType())
                .size(asset.getSize())
                .uploadedByUserId(asset.getUploadedByUserId())
                .build();
    }
}
