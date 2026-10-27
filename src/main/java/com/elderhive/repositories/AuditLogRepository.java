package com.elderhive.repositories;

import com.elderhive.entities.AuditLog;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Backs the Admin > Audit Log viewer (Prompt 96) - same nullable-predicate JPQL shape
    // as UserRepository#findByCriteria's own precedent for entityType/actionType.
    // `from`/`to` use COALESCE(..., a.timestamp) rather than a standalone ":from IS NULL"
    // check - pgjdbc can't infer a bind parameter's type when its ONLY occurrence in the
    // query is a bare "? IS NULL" comparison (no typed column context at that position),
    // which fails with "could not determine data type of parameter" for a null Instant
    // specifically (unlike the String/enum params above, which Postgres defaults to
    // text). COALESCE always pairs the parameter with the typed `a.timestamp` column, so
    // the driver can resolve it even when the value itself is null.
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:entityType IS NULL OR a.entityType = :entityType)
            AND (:actionType IS NULL OR a.actionType = :actionType)
            AND a.timestamp >= COALESCE(:from, a.timestamp)
            AND a.timestamp <= COALESCE(:to, a.timestamp)
    """)
    Page<AuditLog> findByCriteria(@Param("entityType") EntityTypeEnum entityType, @Param("actionType") ActionType actionType,
                                   @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
