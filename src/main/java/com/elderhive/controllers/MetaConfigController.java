package com.elderhive.controllers;

import com.elderhive.dtos.MetaConfig.MetaConfigRequest;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.AdminConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reads are open to any authenticated user — a config context isn't necessarily
 * admin-only to CONSUME (e.g. a banner or feature-flag context other parts of the app
 * read), only to CHANGE. Mutations (upsert/delete) are gated by
 * @adminPermissionGuard.has('CONFIG_MANAGE'), Super-Admin-only in practice today since
 * the seeder (Prompt 24) keeps CONFIG_MANAGE out of the curated "Admin" role, same as
 * ROLE_MANAGE.
 */
@RestController
@RequestMapping("/meta-configs")
@RequiredArgsConstructor
public class MetaConfigController {

    private final AdminConfigService adminConfigService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<List<MetaConfigResponse>>> list() {
        return ApiResponse.successResponse(adminConfigService.listConfigs(), "Configs fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{context}")
    public ResponseEntity<ResponseModel<MetaConfigResponse>> get(@PathVariable String context) throws GenericException {
        return ApiResponse.successResponse(adminConfigService.getConfig(context), "Config fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('CONFIG_MANAGE')")
    @PutMapping("/{context}")
    public ResponseEntity<ResponseModel<MetaConfigResponse>> upsert(@PathVariable String context,
                                                                      @Valid @RequestBody MetaConfigRequest request)
            throws GenericException {
        return ApiResponse.successResponse(adminConfigService.upsertConfig(context, request), "Config saved successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('CONFIG_MANAGE')")
    @DeleteMapping("/{context}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable String context) throws GenericException {
        adminConfigService.deleteConfig(context);
        return ApiResponse.successResponse("Config deleted successfully", "Config deleted successfully");
    }
}
