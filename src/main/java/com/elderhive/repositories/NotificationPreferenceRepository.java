package com.elderhive.repositories;

import com.elderhive.entities.NotificationPreference;
import com.elderhive.enums.NotificationTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndType(Long userId, NotificationTypeEnum type);
}
