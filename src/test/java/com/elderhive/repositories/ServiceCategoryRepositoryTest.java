package com.elderhive.repositories;

import com.elderhive.entities.ServiceCategory;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V24/V25's migrations match the entity
 * mapping, the name unique constraint, JPA auditing, and that the starter seed data
 * (Prompt 71) actually landed.
 */
@SpringBootTest
@Transactional
class ServiceCategoryRepositoryTest {

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistingAndReloadingACategoryPopulatesAuditFieldsAndEveryColumn() {
        ServiceCategory saved = serviceCategoryRepository.save(ServiceCategory.builder()
                .name("Overnight Care").description("Overnight in-home care.").icon("moon")
                .status(ServiceCategoryStatusEnum.ACTIVE).build());
        entityManager.flush();
        entityManager.clear();

        ServiceCategory reloaded = serviceCategoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Overnight Care");
        assertThat(reloaded.getDescription()).isEqualTo("Overnight in-home care.");
        assertThat(reloaded.getIcon()).isEqualTo("moon");
        assertThat(reloaded.getStatus()).isEqualTo(ServiceCategoryStatusEnum.ACTIVE);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void theNameColumnIsUnique() {
        serviceCategoryRepository.save(ServiceCategory.builder().name("Pet Walking").status(ServiceCategoryStatusEnum.ACTIVE).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            serviceCategoryRepository.save(ServiceCategory.builder().name("Pet Walking").status(ServiceCategoryStatusEnum.ACTIVE).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByNameLocatesAnExistingCategory() {
        serviceCategoryRepository.save(ServiceCategory.builder().name("Errand Running").status(ServiceCategoryStatusEnum.ACTIVE).build());
        entityManager.flush();

        assertThat(serviceCategoryRepository.findByName("Errand Running")).isPresent();
        assertThat(serviceCategoryRepository.findByName("Not A Real Category")).isEmpty();
    }

    @Test
    void theStarterSeedSetFromPrompt71IsPresent() {
        assertThat(serviceCategoryRepository.findByName("Temple Visit")).isPresent();
        assertThat(serviceCategoryRepository.findByName("Hospital Visit")).isPresent();
        assertThat(serviceCategoryRepository.findByName("Grocery Shopping")).isPresent();
        assertThat(serviceCategoryRepository.findByName("Companion Care")).isPresent();
        assertThat(serviceCategoryRepository.findByName("Medication Assistance")).isPresent();
        assertThat(serviceCategoryRepository.findByName("Physiotherapy")).isPresent();
    }
}
