package com.elderhive.services.impl;

import com.cloudinary.utils.ObjectUtils;
import com.elderhive.config.CloudinaryConfig;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Ported from ElderSphere's own FileStorageServiceImpl, renamed path -> storageKey to match
 * this prompt's entity field naming, and with resolveUrl added since FileAsset (unlike
 * ElderSphere's) doesn't persist a url column - the URL is always re-derived from the key.
 * Stores uploaded files on Cloudinary when configured (see CloudinaryConfig), organized
 * under an {@code elderhive/<folder>} folder; otherwise falls back to local disk under
 * {@code app.file-storage.upload-dir} (yyyy/MM subfolders), served via WebConfig's resource
 * handler at {@code /uploads/**}. The two modes are distinguished purely by the storageKey
 * format: a Cloudinary-stored key is prefixed {@code "cloudinary:<resourceType>:"}; anything
 * else is treated as a local-disk-relative path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final String CLOUDINARY_PREFIX = "cloudinary:";

    private final CloudinaryConfig cloudinaryConfig;

    @Value("${app.file-storage.upload-dir}")
    private String uploadDir;

    @Value("${app.file-storage.public-base-url}")
    private String publicBaseUrl;

    @Override
    public StoredFile store(MultipartFile file, String folder) throws GenericException {
        if (file == null || file.isEmpty()) {
            throw new GenericException(ExceptionCodeEnum.BAD_REQUEST, "File is empty");
        }
        return cloudinaryConfig.isConfigured() ? storeToCloudinary(file, folder) : storeToLocalDisk(file);
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        if (storageKey.startsWith(CLOUDINARY_PREFIX)) {
            deleteFromCloudinary(storageKey);
        } else {
            deleteFromLocalDisk(storageKey);
        }
    }

    @Override
    public String resolveUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return null;
        if (storageKey.startsWith(CLOUDINARY_PREFIX)) {
            String[] parts = storageKey.split(":", 3);
            if (parts.length != 3) {
                log.warn("Malformed Cloudinary storage key, cannot resolve URL: {}", storageKey);
                return null;
            }
            String cloudinaryResourceType = parts[1];
            String publicId = parts[2];
            // Cloudinary's standard, version-less delivery URL - documented to always fetch
            // the asset's current version, so no version segment needs to be persisted.
            return "https://res.cloudinary.com/" + cloudinaryConfig.getCloudName() + "/" + cloudinaryResourceType + "/upload/" + publicId;
        }
        return publicBaseUrl + "/" + storageKey;
    }

    // ====== Cloudinary ======

    private StoredFile storeToCloudinary(MultipartFile file, String folder) throws GenericException {
        String cloudinaryResourceType = resolveCloudinaryResourceType(file.getContentType());
        String cloudinaryFolder = "elderhive/" + folder;
        String publicId = cloudinaryFolder + "/" + baseName(file.getOriginalFilename()) + "_" + UUID.randomUUID();
        try {
            Map<?, ?> result = cloudinaryConfig.getCloudinary().uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", cloudinaryResourceType,
                    "overwrite", false
            ));
            String secureUrl = String.valueOf(result.get("secure_url"));
            String storedPublicId = String.valueOf(result.get("public_id"));
            String storageKey = CLOUDINARY_PREFIX + cloudinaryResourceType + ":" + storedPublicId;
            return new StoredFile(storageKey, secureUrl);
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage());
            throw new GenericException(ExceptionCodeEnum.FILE_STORAGE_FAILED, "Failed to upload file to Cloudinary");
        }
    }

    private void deleteFromCloudinary(String storageKey) {
        String[] parts = storageKey.split(":", 3);
        if (parts.length != 3) {
            log.warn("Malformed Cloudinary storage key, skipping delete: {}", storageKey);
            return;
        }
        String cloudinaryResourceType = parts[1];
        String publicId = parts[2];
        try {
            cloudinaryConfig.getCloudinary().uploader().destroy(publicId, ObjectUtils.asMap("resource_type", cloudinaryResourceType));
        } catch (Exception e) {
            log.warn("Failed to delete Cloudinary file {}: {}", publicId, e.getMessage());
        }
    }

    /** Cloudinary buckets every upload into "image", "video", or "raw" (everything else - PDF, Word, Excel, ...). */
    private String resolveCloudinaryResourceType(String contentType) {
        if (contentType == null) return "raw";
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("video/")) return "video";
        return "raw";
    }

    /** Strips the extension and sanitizes an uploaded filename for use inside a Cloudinary public_id. */
    private String baseName(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename != null ? originalFilename : "file");
        int dotIndex = cleaned.lastIndexOf('.');
        String withoutExtension = dotIndex >= 0 ? cleaned.substring(0, dotIndex) : cleaned;
        return withoutExtension.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // ====== Local disk ======

    private StoredFile storeToLocalDisk(MultipartFile file) throws GenericException {
        try {
            String subDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path targetDir = Paths.get(uploadDir, subDir);
            Files.createDirectories(targetDir);

            String originalName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalName.substring(dotIndex);
            }
            String storedName = UUID.randomUUID() + extension;
            Path targetPath = targetDir.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath);

            String relativePath = subDir + "/" + storedName;
            return new StoredFile(relativePath, publicBaseUrl + "/" + relativePath);
        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage());
            throw new GenericException(ExceptionCodeEnum.FILE_STORAGE_FAILED, "Failed to store file");
        }
    }

    private void deleteFromLocalDisk(String relativePath) {
        try {
            Files.deleteIfExists(Paths.get(uploadDir, relativePath));
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", relativePath, e.getMessage());
        }
    }
}
