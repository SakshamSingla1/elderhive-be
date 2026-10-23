package com.elderhive.repositories;

import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.UserTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, Long> {

    List<UserRoleMapping> findByUserId(Long userId);

    // Backs AdminUserController's listUsers response (Prompt 93) - one batched query for
    // every row on a page rather than one findByUserId call per row, same precedent
    // SearchServiceImpl's own findAllById batching for caretaker users uses.
    List<UserRoleMapping> findByUserIdIn(List<Long> userIds);

    // Backs AdminUserServiceImpl's grant/revoke (Prompt 93) - whether/which mapping row
    // already exists for this exact (user, role) combination, matching the table's own
    // unique (user_id, user_type) constraint (see UserAndUserRoleMappingTest).
    boolean existsByUserIdAndUserType(Long userId, UserTypeEnum userType);

    Optional<UserRoleMapping> findByUserIdAndUserType(Long userId, UserTypeEnum userType);
}
