package com.elderhive.repositories;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Backs AdminUserController's list/search endpoint (Prompt 93) - same nullable-
    // predicate JPQL shape as EmergencyAlertRepository's own findByCriteria precedent;
    // no ORDER BY here either, sorting is left entirely to the caller's Pageable. `search`
    // is expected pre-wrapped as a "%...%" LIKE pattern (or null), already lower-cased, by
    // the caller - same convention SearchServiceImpl's own pattern-building uses.
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR LOWER(u.email) LIKE :search OR LOWER(u.fullName) LIKE :search)
            AND (:userType IS NULL OR u.userType = :userType)
            AND (:status IS NULL OR u.status = :status)
    """)
    Page<User> findByCriteria(@Param("search") String search, @Param("userType") UserTypeEnum userType,
                               @Param("status") UserStatusEnum status, Pageable pageable);
}
