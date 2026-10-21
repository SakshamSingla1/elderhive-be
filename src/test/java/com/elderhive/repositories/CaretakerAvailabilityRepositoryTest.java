package com.elderhive.repositories;

import com.elderhive.entities.CaretakerAvailability;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.DayOfWeekEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres - confirms V22's migration matches the entity
 * mapping, the caretaker_profiles cascade, and JPA auditing.
 */
@SpringBootTest
@Transactional
class CaretakerAvailabilityRepositoryTest {

    @Autowired
    private CaretakerAvailabilityRepository availabilityRepository;

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

    private CaretakerProfile persistProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
    }

    @Test
    void persistingAndReloadingASlotPopulatesAuditFieldsAndEveryColumn() {
        User caretaker = persistUser("ca-roundtrip@example.com");
        CaretakerProfile profile = persistProfile(caretaker.getId());

        CaretakerAvailability saved = availabilityRepository.save(CaretakerAvailability.builder()
                .caretakerId(profile.getId()).dayOfWeek(DayOfWeekEnum.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build());
        entityManager.flush();
        entityManager.clear();

        CaretakerAvailability reloaded = availabilityRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCaretakerId()).isEqualTo(profile.getId());
        assertThat(reloaded.getDayOfWeek()).isEqualTo(DayOfWeekEnum.MONDAY);
        assertThat(reloaded.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(reloaded.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByCaretakerIdOrderByDayOfWeekAscStartTimeAscReturnsOnlyThatCaretakersSlots() {
        User caretaker1 = persistUser("ca-find1@example.com");
        User caretaker2 = persistUser("ca-find2@example.com");
        CaretakerProfile profile1 = persistProfile(caretaker1.getId());
        CaretakerProfile profile2 = persistProfile(caretaker2.getId());
        availabilityRepository.save(CaretakerAvailability.builder()
                .caretakerId(profile1.getId()).dayOfWeek(DayOfWeekEnum.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).build());
        availabilityRepository.save(CaretakerAvailability.builder()
                .caretakerId(profile1.getId()).dayOfWeek(DayOfWeekEnum.MONDAY)
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(17, 0)).build());
        availabilityRepository.save(CaretakerAvailability.builder()
                .caretakerId(profile2.getId()).dayOfWeek(DayOfWeekEnum.TUESDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build());
        entityManager.flush();

        List<CaretakerAvailability> slots = availabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(profile1.getId());

        assertThat(slots).hasSize(2);
        assertThat(slots).extracting(CaretakerAvailability::getCaretakerId).containsOnly(profile1.getId());
        // Same-day slots for the same caretaker still order by start time ascending.
        assertThat(slots.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(1).getStartTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void deletingTheCaretakerProfileCascadesToItsAvailabilitySlots() {
        User caretaker = persistUser("ca-cascade@example.com");
        CaretakerProfile profile = persistProfile(caretaker.getId());
        CaretakerAvailability saved = availabilityRepository.save(CaretakerAvailability.builder()
                .caretakerId(profile.getId()).dayOfWeek(DayOfWeekEnum.FRIDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build());
        entityManager.flush();

        caretakerProfileRepository.delete(profile);
        entityManager.flush();
        entityManager.clear();

        assertThat(availabilityRepository.findById(saved.getId())).isEmpty();
    }
}
