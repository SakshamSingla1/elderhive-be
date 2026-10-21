package com.elderhive.dtos.File;

import lombok.Builder;
import lombok.Data;

/** url is computed on read (FileStorageService#resolveUrl), never persisted on FileAsset itself. */
@Data
@Builder
public class FileAssetResponse {
    private Long id;
    private String url;
    private String originalFilename;
    private String contentType;
    private Long size;
    private Long uploadedByUserId;
}
