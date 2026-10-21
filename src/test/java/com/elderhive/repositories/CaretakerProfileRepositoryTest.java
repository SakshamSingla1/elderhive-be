package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V20's migration matches the entity
 * mapping, the user_id uniqueness/cascade, the caretaker_profile_specialties many-to-many
 * join table (converted from a free-text element collection to a real ServiceCategory FK
 * by Prompt 72), and JPA auditing.
 */
@SpringBootTest
@Transactional
class CaretakerProfileRepositoryTest {

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.CARETAKER).status(UserStatusEnum.ACTIVE).build());
    }

    private ServiceCategory persistCategory(String name) {
        return serviceCategoryRepository.save(ServiceCategory.builder().name(name).status(ServiceCategoryStatusEnum.ACTIVE).build());
    }

    @Test
    void persistingAndReloadingAProfilePopulatesAuditFieldsAndEveryColumnIncludingSpecialties() {
        User caretaker = persistUser("cp-roundtrip@example.com");
        ServiceCategory mobility = persistCategory("cp-roundtrip-Mobility Assistance");
        ServiceCategory medication = persistCategory("cp-roundtrip-Medication Management");

        CaretakerProfile saved = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).bio("Caring and patient").yearsOfExperience(5)
                .hourlyRate(new BigDecimal("25.50")).ratingAverage(4.2)
                .verificationStatus(CaretakerVerificationStatusEnum.PENDING)
                .latitude(12.9716).longitude(77.5946).serviceRadiusKm(15.0)
                .specialties(Set.of(mobility, medication))
                .build());
        entityManager.flush();
        entityManager.clear();

        CaretakerProfile reloaded = caretakerProfileRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(caretaker.getId());
        assertThat(reloaded.getBio()).isEqualTo("Caring and patient");
        assertThat(reloaded.getHourlyRate()).isEqualByComparingTo("25.50");
        assertThat(reloaded.getLatitude()).isEqualTo(12.9716);
        assertThat(reloaded.getLongitude()).isEqualTo(77.5946);
        assertThat(reloaded.getServiceRadiusKm()).isEqualTo(15.0);
        assertThat(reloaded.getSpecialties()).extracting(ServiceCategory::getId)
                .containsExactlyInAnyOrder(mobility.getId(), medication.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void addingAndRemovingASpecialtyUpdatesTheJoinTableWithoutTouchingTheCategoryItself() {
        User caretaker = persistUser("cp-specialty-update@example.com");
        ServiceCategory mobility = persistCategory("cp-specialty-update-Mobility Assistance");
        ServiceCategory medication = persistCategory("cp-specialty-update-Medication Management");
        CaretakerProfile saved = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING)
                .specialties(Set.of(mobility)).build());
        entityManager.flush();
        entityManager.clear();

        CaretakerProfile reloaded = caretakerProfileRepository.findById(saved.getId()).orElseThrow();
        // A mutable HashSet, not Set.of() - Hibernate's merge tries to clear() the existing
        // managed collection in place, which throws UnsupportedOperationException against
        // an immutable Set.
        reloaded.setSpecialties(new HashSet<>(Set.of(medication)));
        caretakerProfileRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        CaretakerProfile reReloaded = caretakerProfileRepository.findById(saved.getId()).orElseThrow();
        assertThat(reReloaded.getSpecialties()).extracting(ServiceCategory::getId).containsExactly(medication.getId());
        assertThat(serviceCategoryRepository.findById(mobility.getId())).isPresent();
    }

    @Test
    void deletingAServiceCategoryCascadesToItsSpecialtyLinksButNotTheCaretakerProfile() {
        User caretaker = persistUser("cp-category-cascade@example.com");
        ServiceCategory mobility = persistCategory("cp-category-cascade-Mobility Assistance");
        CaretakerProfile saved = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING)
                .specialties(Set.of(mobility)).build());
        entityManager.flush();
        // Clears the persistence context first - otherwise the still-managed `saved`
        // CaretakerProfile's own in-memory specialties collection keeps referencing
        // `mobility` right as it's deleted, which Hibernate can't reconcile at flush time
        // (a stale same-session reference, not a real data problem - the DB's own
        // ON DELETE CASCADE handles the join row correctly either way).
        entityManager.clear();

        serviceCategoryRepository.delete(mobility);
        entityManager.flush();
        entityManager.clear();

        CaretakerProfile reloaded = caretakerProfileRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSpecialties()).isEmpty();
    }

    @Test
    void findByUserIdAndExistsByUserIdWork() {
        User caretaker = persistUser("cp-byuser@example.com");
        caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
        entityManager.flush();

        assertThat(caretakerProfileRepository.findByUserId(caretaker.getId())).isPresent();
        assertThat(caretakerProfileRepository.existsByUserId(caretaker.getId())).isTrue();
        assertThat(caretakerProfileRepository.existsByUserId(999999L)).isFalse();
    }

    @Test
    void aUserCanOnlyHaveOneCaretakerProfile() {
        User caretaker = persistUser("cp-unique@example.com");
        caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            caretakerProfileRepository.save(CaretakerProfile.builder()
                    .userId(caretaker.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheCaretakerUserCascadesToTheirProfile() {
        User caretaker = persistUser("cp-cascade@example.com");
        CaretakerProfile saved = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
        entityManager.flush();

        userRepository.delete(caretaker);
        entityManager.flush();
        entityManager.clear();

        assertThat(caretakerProfileRepository.findById(saved.getId())).isEmpty();
    }
}
