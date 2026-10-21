package com.elderhive.controllers;

import com.elderhive.entities.FileAsset;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.FileAssetRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.AdminPermissionGuard;
import com.elderhive.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Generic download entry point for any FileAsset - today, that's exclusively caretaker
 * verification documents (sensitive PII), so access is deliberately narrow: the uploader
 * themselves, or an ADMIN/SUPER_ADMIN with CARETAKER_VERIFY. A future reuse of FileAsset for
 * something public (e.g. a profile photo) would need its own, separately-considered access
 * rule here - this is not a general-purpose public file server.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final AdminPermissionGuard adminPermissionGuard;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@PathVariable Long id) throws GenericException {
        User caller = currentUser();
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.FILE_NOT_FOUND, "File not found"));

        boolean isUploader = Objects.equals(asset.getUploadedByUserId(), caller.getId());
        boolean isVerifyingAdmin = ADMIN_TIER.contains(caller.getUserType()) && adminPermissionGuard.has("CARETAKER_VERIFY");
        if (!isUploader && !isVerifyingAdmin) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to this file");
        }

        String url = fileStorageService.resolveUrl(asset.getStorageKey());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
