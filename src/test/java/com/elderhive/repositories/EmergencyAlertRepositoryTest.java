package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.EmergencyAlert;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V29's migration matches the entity
 * mapping, every FK's CASCADE/RESTRICT/SET NULL behavior, the finder/count methods later
 * prompts (82-84) will rely on, and JPA auditing.
 */
@SpringBootTest
@Transactional
class EmergencyAlertRepositoryTest {

    @Autowired
    private EmergencyAlertRepository emergencyAlertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ElderProfileRepository elderProfileRepository;

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private ElderProfile persistElderProfile(Long familyUserId) {
        return elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
    }

    private CaretakerProfile persistCaretakerProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.VERIFIED).build());
    }

    private EmergencyAlert.EmergencyAlertBuilder newAlert(Long elderProfileId, Long triggeredByUserId) {
        return EmergencyAlert.builder()
                .elderProfileId(elderProfileId).triggeredByUserId(triggeredByUserId)
                .latitude(12.9716).longitude(77.5946)
                .status(EmergencyAlertStatusEnum.TRIGGERED);
    }

    @Test
    void persistingAndReloadingAnAlertPopulatesAuditFieldsAndEveryColumn() {
        User family = persistUser("alert-roundtrip-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = persistElderProfile(family.getId());

        EmergencyAlert saved = emergencyAlertRepository.save(newAlert(elder.getId(), family.getId())
                .address("MG Road, Bengaluru").build());
        entityManager.flush();
        entityManager.clear();

        EmergencyAlert reloaded = emergencyAlertRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getElderProfileId()).isEqualTo(elder.getId());
        assertThat(reloaded.getTriggeredByUserId()).isEqualTo(family.getId());
        assertThat(reloaded.getLatitude()).isEqualTo(12.9716);
        assertThat(reloaded.getLongitude()).isEqualTo(77.5946);
        assertThat(reloaded.getAddress()).isEqualTo("MG Road, Bengaluru");
        assertThat(reloaded.getStatus()).isEqualTo(EmergencyAlertStatusEnum.TRIGGERED);
        assertThat(reloaded.getRespondingCaretakerId()).isNull();
        assertThat(reloaded.getResponseTimeSeconds()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void addressIsNullWhenReverseGeocodingDidNotResolveOne() {
        User family = persistUser("alert-noaddress-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = persistElderProfile(family.getId());

        EmergencyAlert saved = emergencyAlertRepository.save(newAlert(elder.getId(), family.getId()).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(emergencyAlertRepository.findById(saved.getId()).orElseThrow().getAddress()).isNull();
    }

    @Test
    void deletingTheElderProfileCascadesToItsAlerts() {
        User family = persistUser("alert-cascade-elder-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = persistElderProfile(family.getId());

        EmergencyAlert saved = emergencyAlertRepository.save(newAlert(elder.getId(), family.getId()).build());
        entityManager.flush();

        elderProfileRepository.delete(elder);
        entityManager.flush();
        entityManager.clear();

        assertThat(emergencyAlertRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheTriggeringUserCascadesToTheirAlerts() {
        // triggeredByUserId deliberately NOT family (the elder profile's own owner) - an
        // elder with their own linked login can trigger their own alert - so deleting it
        // exercises triggered_by_user_id's own CASCADE in isolation from
        // elder_profile_id's (the elder profile and its owning family stay untouched).
        User family = persistUser("alert-cascade-triggeredby-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User triggeringUser = persistUser("alert-cascade-triggeredby-trigger@example.com", UserTypeEnum.ELDER);
        ElderProfile elder = persistElderProfile(family.getId());

        EmergencyAlert saved = emergencyAlertRepository.save(newAlert(elder.getId(), triggeringUser.getId()).build());
        entityManager.flush();

        userRepository.delete(triggeringUser);
        entityManager.flush();
        entityManager.clear();

        assertThat(emergencyAlertRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheRespondingCaretakerSetsTheReferenceNullRatherThanDeletingTheAlert() {
        User family = persistUser("alert-setnull-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("alert-setnull-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());

        EmergencyAlert saved = emergencyAlertRepository.save(newAlert(elder.getId(), family.getId())
                .status(EmergencyAlertStatusEnum.ACKNOWLEDGED).respondingCaretakerId(caretaker.getId()).build());
        entityManager.flush();

        caretakerProfileRepository.deleteById(caretaker.getId());
        entityManager.flush();
        entityManager.clear();

        EmergencyAlert reloaded = emergencyAlertRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRespondingCaretakerId()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(EmergencyAlertStatusEnum.ACKNOWLEDGED);
    }

    @Test
    void findByElderProfileIdAndFindByStatusScopeCorrectly() {
        User familyA = persistUser("alert-byid-familyA@example.com", UserTypeEnum.FAMILY_MEMBER);
        User familyB = persistUser("alert-byid-familyB@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elderA = persistElderProfile(familyA.getId());
        ElderProfile elderB = persistElderProfile(familyB.getId());

        emergencyAlertRepository.save(newAlert(elderA.getId(), familyA.getId()).status(EmergencyAlertStatusEnum.TRIGGERED).build());
        emergencyAlertRepository.save(newAlert(elderA.getId(), familyA.getId()).status(EmergencyAlertStatusEnum.RESOLVED).build());
        emergencyAlertRepository.save(newAlert(elderB.getId(), familyB.getId()).status(EmergencyAlertStatusEnum.TRIGGERED).build());
        entityManager.flush();

        assertThat(emergencyAlertRepository.findByElderProfileId(elderA.getId(), PageRequest.of(0, 10)).getContent()).hasSize(2);
        assertThat(emergencyAlertRepository.findByStatus(EmergencyAlertStatusEnum.TRIGGERED, PageRequest.of(0, 10)).getContent()).hasSize(2);
        assertThat(emergencyAlertRepository.countByStatus(EmergencyAlertStatusEnum.RESOLVED)).isEqualTo(1);
    }

    @Test
    void countByElderProfileIdAndStatusInCountsOnlyTheRequestedStatuses() {
        User family = persistUser("alert-countstatus-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = persistElderProfile(family.getId());

        emergencyAlertRepository.save(newAlert(elder.getId(), family.getId()).status(EmergencyAlertStatusEnum.TRIGGERED).build());
        emergencyAlertRepository.save(newAlert(elder.getId(), family.getId()).status(EmergencyAlertStatusEnum.RESOLVED).build());
        entityManager.flush();

        assertThat(emergencyAlertRepository.countByElderProfileIdAndStatusIn(
                elder.getId(), List.of(EmergencyAlertStatusEnum.TRIGGERED, EmergencyAlertStatusEnum.ACKNOWLEDGED))).isEqualTo(1);
        assertThat(emergencyAlertRepository.countByElderProfileIdAndStatusIn(
                elder.getId(), List.of(EmergencyAlertStatusEnum.RESOLVED))).isEqualTo(1);
    }

    @Test
    void findTop10ByOrderByCreatedAtDescReturnsMostRecentFirst() {
        User family = persistUser("alert-recent-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = persistElderProfile(family.getId());

        EmergencyAlert first = emergencyAlertRepository.save(newAlert(elder.getId(), family.getId()).build());
        entityManager.flush();
        EmergencyAlert second = emergencyAlertRepository.save(newAlert(elder.getId(), family.getId()).build());
        entityManager.flush();

        List<EmergencyAlert> recent = emergencyAlertRepository.findTop10ByOrderByCreatedAtDesc();
        assertThat(recent.get(0).getId()).isEqualTo(second.getId());
        assertThat(recent).extracting(EmergencyAlert::getId).contains(first.getId(), second.getId());
    }

    @Test
    void savingAnAlertWithAnUnknownElderProfileFailsTheForeignKey() {
        User family = persistUser("alert-badelder-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> {
            emergencyAlertRepository.save(newAlert(999999999L, family.getId()).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
