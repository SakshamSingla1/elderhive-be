package com.elderhive.controllers;

import com.elderhive.dtos.AuditLog.AuditLogResponse;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Read-only - the audit trail itself is written by AuditLogService#record from every
 * compliance-sensitive call site, never through this controller. ROLE_MANAGE-gated, same
 * as RoleController/PermissionController - Super-Admin-only in practice today since the
 * seeder keeps ROLE_MANAGE out of the curated "Admin" role (AdminBootstrapSeeder, Prompt 24).
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<AuditLogResponse>>> search(
            @RequestParam(required = false) EntityTypeEnum entityType,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return ApiResponse.successResponse(auditLogService.search(entityType, actionType, from, to, pageable),
                "Audit logs fetched successfully");
    }
}
