package com.elderhive.services.impl;

import com.elderhive.dtos.AuditLog.AuditLogResponse;
import com.elderhive.entities.AuditLog;
import com.elderhive.entities.User;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.repositories.AuditLogRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

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

    @Override
    public Page<AuditLogResponse> search(EntityTypeEnum entityType, ActionType actionType, Instant from, Instant to, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findByCriteria(entityType, actionType, from, to, pageable);

        // Batch-resolved the same way AdminUserServiceImpl#listUsers resolves roles - one
        // lookup for the whole page rather than one per row.
        Map<Long, String> emailsByUserId = userRepository
                .findAllById(page.getContent().stream().map(AuditLog::getCreatedBy).filter(java.util.Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        return page.map(log -> toResponse(log, emailsByUserId.get(log.getCreatedBy())));
    }

    private AuditLogResponse toResponse(AuditLog log, String actorEmail) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .actionType(log.getActionType())
                .actorUserId(log.getCreatedBy())
                .actorEmail(actorEmail)
                .data(log.getData())
                .timestamp(log.getTimestamp())
                .build();
    }
}
