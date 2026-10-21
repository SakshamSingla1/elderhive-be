package com.elderhive.repositories;

import com.elderhive.entities.ServiceCategory;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    Optional<ServiceCategory> findByName(String name);

    @Query("""
            SELECT sc FROM ServiceCategory sc
            WHERE (:status IS NULL OR sc.status = :status)
    """)
    Page<ServiceCategory> findByCriteria(@Param("status") ServiceCategoryStatusEnum status, Pageable pageable);
}
