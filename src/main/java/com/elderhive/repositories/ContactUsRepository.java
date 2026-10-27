package com.elderhive.repositories;

import com.elderhive.entities.ContactUs;
import com.elderhive.enums.ContactUsStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactUsRepository extends JpaRepository<ContactUs, Long> {

    // Same nullable-predicate JPQL shape as UserRepository#findByCriteria's own
    // precedent. `search` is expected pre-wrapped as a "%...%" LIKE pattern (or null),
    // already lower-cased, by the caller.
    @Query("""
            SELECT c FROM ContactUs c
            WHERE (:search IS NULL OR LOWER(c.name) LIKE :search OR LOWER(c.email) LIKE :search OR LOWER(c.message) LIKE :search)
            AND (:status IS NULL OR c.status = :status)
    """)
    Page<ContactUs> findByCriteria(@Param("search") String search, @Param("status") ContactUsStatusEnum status, Pageable pageable);
}
