package com.elderhive.services;

import com.elderhive.entities.AuditLog;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.repositories.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres (docker-compose.yml) via the app's own datasource
 * config — the first real entity+repo+service wired together, worth confirming it
 * actually persists, not just that it compiles.
 */
@SpringBootTest
@Transactional
class AuditLogServiceImplTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void recordPersistsAllFieldsAndStampsTimestamp() {
        Instant before = Instant.now();

        AuditLog saved = auditLogService.record(
                EntityTypeEnum.ROLE, "42", ActionType.GRANT, 7L, "{\"roleName\":\"Super Admin\"}");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull().isAfterOrEqualTo(before);

        Optional<AuditLog> reloaded = auditLogRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getEntityType()).isEqualTo(EntityTypeEnum.ROLE);
        assertThat(reloaded.get().getEntityId()).isEqualTo("42");
        assertThat(reloaded.get().getActionType()).isEqualTo(ActionType.GRANT);
        assertThat(reloaded.get().getCreatedBy()).isEqualTo(7L);
        assertThat(reloaded.get().getData()).isEqualTo("{\"roleName\":\"Super Admin\"}");
    }
}
