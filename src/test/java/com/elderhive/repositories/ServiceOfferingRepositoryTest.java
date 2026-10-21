package com.elderhive.repositories;

import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V26's migration matches the entity
 * mapping, the category_id FK (with no ON DELETE clause - RESTRICT by default), and JPA
 * auditing.
 */
@SpringBootTest
@Transactional
class ServiceOfferingRepositoryTest {

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private EntityManager entityManager;

    private ServiceCategory persistCategory(String name) {
        return serviceCategoryRepository.save(ServiceCategory.builder().name(name).status(ServiceCategoryStatusEnum.ACTIVE).build());
    }

    @Test
    void persistingAndReloadingAnOfferingPopulatesAuditFieldsAndEveryColumn() {
        ServiceCategory category = persistCategory("offering-roundtrip-Temple Visit");

        ServiceOffering saved = serviceOfferingRepository.save(ServiceOffering.builder()
                .name("Weekly Temple Visit").categoryId(category.getId()).description("A weekly visit.")
                .basePrice(new BigDecimal("40.00")).durationMinutes(90).status(ServiceOfferingStatusEnum.ACTIVE).build());
        entityManager.flush();
        entityManager.clear();

        ServiceOffering reloaded = serviceOfferingRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Weekly Temple Visit");
        assertThat(reloaded.getCategoryId()).isEqualTo(category.getId());
        assertThat(reloaded.getDescription()).isEqualTo("A weekly visit.");
        assertThat(reloaded.getBasePrice()).isEqualByComparingTo("40.00");
        assertThat(reloaded.getDurationMinutes()).isEqualTo(90);
        assertThat(reloaded.getStatus()).isEqualTo(ServiceOfferingStatusEnum.ACTIVE);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void deletingACategoryThatStillHasOfferingsIsRejected() {
        ServiceCategory category = persistCategory("offering-restrict-Temple Visit");
        serviceOfferingRepository.save(ServiceOffering.builder()
                .name("Weekly Temple Visit").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());
        entityManager.flush();

        // Unlike this project's usual save()-then-flush() violations (translated to Spring's
        // DataIntegrityViolationException by the repository proxy), a delete()'s actual SQL
        // only runs at this explicit flush() - outside the repository method call Spring's
        // exception-translation aspect wraps - so the raw Hibernate exception surfaces here.
        assertThatThrownBy(() -> {
            serviceCategoryRepository.delete(category);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void anOfferingCanBeDeletedWithoutAffectingItsCategory() {
        ServiceCategory category = persistCategory("offering-delete-Temple Visit");
        ServiceOffering saved = serviceOfferingRepository.save(ServiceOffering.builder()
                .name("Weekly Temple Visit").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());
        entityManager.flush();

        serviceOfferingRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(serviceOfferingRepository.findById(saved.getId())).isEmpty();
        assertThat(serviceCategoryRepository.findById(category.getId())).isPresent();
    }
}
