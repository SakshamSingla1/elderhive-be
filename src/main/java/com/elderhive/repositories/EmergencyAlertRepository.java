package com.elderhive.repositories;

import com.elderhive.entities.EmergencyAlert;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, Long> {

    // Backs a future elder-history view and admin oversight listing.
    Page<EmergencyAlert> findByElderProfileId(Long elderProfileId, Pageable pageable);

    // Backs Prompt 87's admin oversight listing (EmergencyAlertController#getAll) - no
    // ORDER BY here, same nullable-predicate JPQL shape as BookingRepository's own
    // findByCriteria precedent; sorting is left entirely to the caller's Pageable.
    @Query("""
            SELECT e FROM EmergencyAlert e
            WHERE (:elderProfileId IS NULL OR e.elderProfileId = :elderProfileId)
            AND (:status IS NULL OR e.status = :status)
    """)
    Page<EmergencyAlert> findByCriteria(@Param("elderProfileId") Long elderProfileId,
                                         @Param("status") EmergencyAlertStatusEnum status,
                                         Pageable pageable);

    // Backs the future caretaker/admin "active alerts" queue (Prompt 84's dispatch
    // override needs to list unclaimed/unresolved alerts).
    Page<EmergencyAlert> findByStatus(EmergencyAlertStatusEnum status, Pageable pageable);

    // Backs Prompt 86's "nearby active alerts" caretaker queue - every non-RESOLVED
    // alert system-wide (TRIGGERED and ACKNOWLEDGED both included, so a caretaker also
    // sees one another caretaker already claimed, not just unclaimed ones), filtered down
    // to "nearby" in the service layer by re-running Prompt 82's own proximity match per
    // alert rather than a second geo query here.
    List<EmergencyAlert> findByStatusIn(List<EmergencyAlertStatusEnum> statuses);

    long countByStatus(EmergencyAlertStatusEnum status);

    // Backs Prompt 83's trigger flow: lets it detect whether this elder profile already
    // has an unresolved alert in flight before creating a duplicate one.
    long countByElderProfileIdAndStatusIn(Long elderProfileId, List<EmergencyAlertStatusEnum> statuses);

    // Recent-activity feed for an admin dashboard - ordered by Auditable's own createdAt
    // since this entity has no separate triggeredAt column (see EmergencyAlert's javadoc).
    List<EmergencyAlert> findTop10ByOrderByCreatedAtDesc();
}
