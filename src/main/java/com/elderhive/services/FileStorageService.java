package com.elderhive.services;

import com.elderhive.exceptions.GenericException;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * Result of a store() call: {@code storageKey} is the opaque key this service later
     * needs back for {@link #delete}/{@link #resolveUrl}, and {@code url} is the
     * publicly-reachable download URL at store time (also re-derivable later from the key
     * alone via {@link #resolveUrl}).
     */
    record StoredFile(String storageKey, String url) {}

    /**
     * Stores the file - on Cloudinary when configured (see CloudinaryConfig), otherwise on
     * local disk under the configured upload directory - organized under a
     * {@code folder}-named subpath either way (e.g. "caretaker-verification-documents").
     */
    StoredFile store(MultipartFile file, String folder) throws GenericException;

    /** Deletes a previously stored file, given the {@code storageKey} its store() call returned. */
    void delete(String storageKey);

    /** Re-derives the public download URL for a previously stored file from its storageKey alone. */
    String resolveUrl(String storageKey);
}
