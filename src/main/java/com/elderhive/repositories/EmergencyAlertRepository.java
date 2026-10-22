package com.elderhive.repositories;

import com.elderhive.entities.EmergencyAlert;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, Long> {

    // Backs a future elder-history view and admin oversight listing.
    Page<EmergencyAlert> findByElderProfileId(Long elderProfileId, Pageable pageable);

    // Backs the future caretaker/admin "active alerts" queue (Prompt 84's dispatch
    // override needs to list unclaimed/unresolved alerts).
    Page<EmergencyAlert> findByStatus(EmergencyAlertStatusEnum status, Pageable pageable);

    long countByStatus(EmergencyAlertStatusEnum status);

    // Backs Prompt 83's trigger flow: lets it detect whether this elder profile already
    // has an unresolved alert in flight before creating a duplicate one.
    long countByElderProfileIdAndStatusIn(Long elderProfileId, List<EmergencyAlertStatusEnum> statuses);

    // Recent-activity feed for an admin dashboard - ordered by Auditable's own createdAt
    // since this entity has no separate triggeredAt column (see EmergencyAlert's javadoc).
    List<EmergencyAlert> findTop10ByOrderByCreatedAtDesc();
}
