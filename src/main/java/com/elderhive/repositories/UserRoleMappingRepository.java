package com.elderhive.repositories;

import com.elderhive.entities.UserRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, Long> {

    List<UserRoleMapping> findByUserId(Long userId);
}
