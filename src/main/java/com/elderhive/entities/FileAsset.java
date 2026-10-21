package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Deliberately stores only the opaque {@code storageKey} FileStorageService needs to
 * locate/delete the file again - not a persisted download URL. A URL is resolved on demand
 * (FileStorageService#resolveUrl) from the key: for a local-disk key that's a lossless
 * {@code publicBaseUrl + "/" + storageKey} reconstruction (the key IS the relative path);
 * for a Cloudinary key (shape {@code cloudinary:<resourceType>:<publicId>}, mirroring
 * ElderSphere's own path format) it's Cloudinary's standard, version-less delivery URL
 * pattern, which is a documented, stable way to fetch an asset's current version.
 */
@Entity
@Table(name = "file_assets")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAsset extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    private Long size;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;
}
