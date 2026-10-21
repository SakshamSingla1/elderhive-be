package com.elderhive.services;

import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.exceptions.GenericException;
import org.springframework.web.multipart.MultipartFile;

public interface FileAssetService {

    FileAssetResponse upload(MultipartFile file, String folder, Long uploadedByUserId) throws GenericException;

    FileAssetResponse getById(Long id) throws GenericException;

    void delete(Long id) throws GenericException;
}
