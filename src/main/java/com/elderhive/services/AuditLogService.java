package com.elderhive.services;

import com.elderhive.entities.AuditLog;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;

/**
 * No callers yet — later phases wire this in as each touches something
 * compliance-sensitive (role grants, medical records, emergency dispatch overrides).
 */
public interface AuditLogService {

    AuditLog record(EntityTypeEnum entityType, String entityId, ActionType actionType, Long actorUserId, String data);
}
