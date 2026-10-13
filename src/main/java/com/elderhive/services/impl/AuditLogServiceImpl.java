package com.elderhive.services.impl;

import com.elderhive.entities.AuditLog;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.repositories.AuditLogRepository;
import com.elderhive.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public AuditLog record(EntityTypeEnum entityType, String entityId, ActionType actionType, Long actorUserId, String data) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .actionType(actionType)
                .createdBy(actorUserId)
                .data(data)
                .build();
        return auditLogRepository.save(auditLog);
    }
}
