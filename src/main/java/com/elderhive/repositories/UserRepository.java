package com.elderhive.repositories;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // Backs AdminDashboardController's own "active users per role" stat (Prompt 94) -
    // called once per UserTypeEnum value rather than a single GROUP BY, so the response
    // always has an entry for every role (including zero), not just the roles that
    // happen to have at least one active user.
    long countByUserTypeAndStatus(UserTypeEnum userType, UserStatusEnum status);
}
