package com.elderhive.dtos.AuditLog;

import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private EntityTypeEnum entityType;
    private String entityId;
    private ActionType actionType;
    private Long actorUserId;
    // Resolved from createdBy via UserRepository at read time (AuditLog itself only
    // stores the id) - null if the acting user has since been deleted.
    private String actorEmail;
    private String data;
    private Instant timestamp;
}
