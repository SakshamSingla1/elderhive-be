package com.elderhive.repositories;

import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    @Query("""
            SELECT so FROM ServiceOffering so
            WHERE (:categoryId IS NULL OR so.categoryId = :categoryId)
            AND (:status IS NULL OR so.status = :status)
    """)
    Page<ServiceOffering> findByCriteria(@Param("categoryId") Long categoryId,
                                          @Param("status") ServiceOfferingStatusEnum status,
                                          Pageable pageable);
}
