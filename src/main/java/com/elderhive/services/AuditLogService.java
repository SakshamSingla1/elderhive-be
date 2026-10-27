package com.elderhive.services;

import com.elderhive.dtos.AuditLog.AuditLogResponse;
import com.elderhive.entities.AuditLog;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * {@link #record} is called by every phase that touches something compliance-sensitive
 * (role grants, emergency dispatch overrides, and so on); {@link #search} backs the
 * read-only Admin > Audit Log viewer (Prompt 96).
 */
public interface AuditLogService {

    AuditLog record(EntityTypeEnum entityType, String entityId, ActionType actionType, Long actorUserId, String data);

    Page<AuditLogResponse> search(EntityTypeEnum entityType, ActionType actionType, Instant from, Instant to, Pageable pageable);
}
