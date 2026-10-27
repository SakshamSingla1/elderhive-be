package com.elderhive.services;

import com.elderhive.dtos.AuditLog.AuditLogResponse;
import com.elderhive.entities.AuditLog;
import com.elderhive.entities.User;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.AuditLogRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    @Autowired
    private UserRepository userRepository;

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

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName("Audit Actor")
                .passwordHash("hashed")
                .userType(UserTypeEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .build());
    }

    @Test
    void searchFiltersByEntityTypeAndActionTypeAndResolvesActorEmail() {
        User actor = persistUser("audit-search-actor@example.com");
        // A random entityId - this is a real, already-populated Postgres instance (not a
        // scratch per-test schema), so other ROLE/GRANT rows may already exist; this test
        // must pick its own row out of that set rather than assume it's the only one.
        String entityId = java.util.UUID.randomUUID().toString();

        auditLogService.record(EntityTypeEnum.ROLE, entityId, ActionType.GRANT, actor.getId(), "{}");
        auditLogService.record(EntityTypeEnum.ROLE, entityId, ActionType.REVOKE, actor.getId(), "{}");
        auditLogService.record(EntityTypeEnum.USER, entityId, ActionType.GRANT, actor.getId(), "{}");

        Page<AuditLogResponse> roleGrants = auditLogService.search(
                EntityTypeEnum.ROLE, ActionType.GRANT, null, null, PageRequest.of(0, 1000));

        assertThat(roleGrants.getContent())
                .filteredOn(log -> entityId.equals(log.getEntityId()))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.getEntityType()).isEqualTo(EntityTypeEnum.ROLE);
                    assertThat(match.getActionType()).isEqualTo(ActionType.GRANT);
                    assertThat(match.getActorUserId()).isEqualTo(actor.getId());
                    assertThat(match.getActorEmail()).isEqualTo("audit-search-actor@example.com");
                });
    }

    @Test
    void searchFiltersByDateRangeAndReturnsEmptyOutsideIt() {
        User actor = persistUser("audit-search-daterange@example.com");
        // A random entityId, not "2"/"42" etc. used by other tests in this class/suite -
        // the unbounded (null `from`) half of this test below matches every row the
        // table already has (a real Postgres instance, not a scratch per-test schema), so
        // this test must pick its own row out of that set rather than assert on the set
        // as a whole.
        String entityId = java.util.UUID.randomUUID().toString();
        auditLogService.record(EntityTypeEnum.USER, entityId, ActionType.CREATE, actor.getId(), "{}");

        Instant farFuture = Instant.now().plus(1, ChronoUnit.DAYS);

        Page<AuditLogResponse> none = auditLogService.search(null, null, farFuture, null, PageRequest.of(0, 20));
        assertThat(none.getContent()).isEmpty();

        List<AuditLogResponse> some = auditLogService
                .search(EntityTypeEnum.USER, ActionType.CREATE, null, farFuture, PageRequest.of(0, 1000))
                .getContent();
        assertThat(some)
                .filteredOn(log -> entityId.equals(log.getEntityId()))
                .singleElement()
                .satisfies(log -> assertThat(log.getActorEmail()).isEqualTo("audit-search-daterange@example.com"));
    }
}
