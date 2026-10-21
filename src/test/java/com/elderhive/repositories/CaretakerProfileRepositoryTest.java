package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V20's migration matches the entity
 * mapping, the user_id uniqueness/cascade, the specialties element-collection table, and
 * JPA auditing.
 */
@SpringBootTest
@Transactional
class CaretakerProfileRepositoryTest {

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.CARETAKER).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void persistingAndReloadingAProfilePopulatesAuditFieldsAndEveryColumnIncludingSpecialties() {
        User caretaker = persistUser("cp-roundtrip@example.com");

        CaretakerProfile saved = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretaker.getId()).bio("Caring and patient").yearsOfExperience(5)
                .hourlyRate(new BigDecimal("25.50")).ratingAverage(4.2)
                .verificationStatus(CaretakerVerificationStatusEnum.PENDING)
                .latitude(12.9716).longitude(77.5946).serviceRadiusKm(15.0)
                .specialties(Set.of("Mobility Assistance", "Medication Management"))
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
        assertThat(reloaded.getSpecialties()).containsExactlyInAnyOrder("Mobility Assistance", "Medication Management");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
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
