package com.elderhive.repositories;

import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.GenderEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V17's migration matches the entity
 * mapping (ddl-auto=validate would fail fast if not), both FK's differing ON DELETE
 * behavior (family_user_id CASCADEs, elder_user_id SETs NULL), the elder_user_id unique
 * constraint, and JPA auditing.
 */
@SpringBootTest
@Transactional
class ElderProfileRepositoryTest {

    @Autowired
    private ElderProfileRepository elderProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void persistingAndReloadingAProfilePopulatesAuditFieldsAndEveryColumn() {
        User family = persistUser("ep-roundtrip-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        ElderProfile saved = elderProfileRepository.save(ElderProfile.builder()
                .familyUserId(family.getId())
                .fullName("Robert Smith")
                .dateOfBirth(LocalDate.of(1945, 3, 12))
                .gender(GenderEnum.MALE)
                .medicalConditions("Hypertension")
                .address("123 Main St")
                .emergencyContactName("Jane Smith")
                .emergencyContactPhone("+1-555-0100")
                .build());
        entityManager.flush();
        entityManager.clear();

        ElderProfile reloaded = elderProfileRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getFamilyUserId()).isEqualTo(family.getId());
        assertThat(reloaded.getElderUserId()).isNull();
        assertThat(reloaded.getFullName()).isEqualTo("Robert Smith");
        assertThat(reloaded.getDateOfBirth()).isEqualTo(LocalDate.of(1945, 3, 12));
        assertThat(reloaded.getGender()).isEqualTo(GenderEnum.MALE);
        assertThat(reloaded.getMedicalConditions()).isEqualTo("Hypertension");
        assertThat(reloaded.getAddress()).isEqualTo("123 Main St");
        assertThat(reloaded.getEmergencyContactName()).isEqualTo("Jane Smith");
        assertThat(reloaded.getEmergencyContactPhone()).isEqualTo("+1-555-0100");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByFamilyUserIdReturnsOnlyThatFamilyMembersProfiles() {
        User family = persistUser("ep-find-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User otherFamily = persistUser("ep-find-other-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Elder A").build());
        elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Elder B").build());
        elderProfileRepository.save(ElderProfile.builder().familyUserId(otherFamily.getId()).fullName("Elder C").build());
        entityManager.flush();

        List<ElderProfile> profiles = elderProfileRepository.findByFamilyUserId(family.getId());

        assertThat(profiles).hasSize(2);
        assertThat(profiles).extracting(ElderProfile::getFullName).containsExactlyInAnyOrder("Elder A", "Elder B");
    }

    @Test
    void findByFamilyUserIdPagedReturnsTheSameScoping() {
        User family = persistUser("ep-find-family-paged@example.com", UserTypeEnum.FAMILY_MEMBER);
        elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Elder A").build());
        entityManager.flush();

        var page = elderProfileRepository.findByFamilyUserId(family.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getFullName()).isEqualTo("Elder A");
    }

    @Test
    void findByElderUserIdFindsTheSelfManagedProfile() {
        User elder = persistUser("ep-find-elder@example.com", UserTypeEnum.ELDER);
        elderProfileRepository.save(ElderProfile.builder().elderUserId(elder.getId()).fullName("Self Managed").build());
        entityManager.flush();

        assertThat(elderProfileRepository.findByElderUserId(elder.getId())).isPresent();
        assertThat(elderProfileRepository.findByElderUserId(999999999L)).isEmpty();
    }

    @Test
    void theSameElderUserIdCannotOwnTwoProfiles() {
        User elder = persistUser("ep-unique-elder@example.com", UserTypeEnum.ELDER);
        elderProfileRepository.save(ElderProfile.builder().elderUserId(elder.getId()).fullName("First").build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            elderProfileRepository.save(ElderProfile.builder().elderUserId(elder.getId()).fullName("Second").build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleProfilesWithNoElderUserIdYetAreAllowed() {
        User family = persistUser("ep-null-elder-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("A").build());
        elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("B").build());
        entityManager.flush();

        assertThat(elderProfileRepository.findByFamilyUserId(family.getId())).hasSize(2);
    }

    @Test
    void deletingTheOwningFamilyMemberCascadesToTheProfile() {
        User family = persistUser("ep-cascade-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = elderProfileRepository.save(
                ElderProfile.builder().familyUserId(family.getId()).fullName("Cascade Me").build());
        entityManager.flush();

        userRepository.delete(family);
        entityManager.flush();
        entityManager.clear();

        assertThat(elderProfileRepository.findById(profile.getId())).isEmpty();
    }

    @Test
    void deletingTheLinkedElderUserOnlyNullsTheLinkRatherThanDeletingTheProfile() {
        User family = persistUser("ep-setnull-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User elder = persistUser("ep-setnull-elder@example.com", UserTypeEnum.ELDER);
        ElderProfile profile = elderProfileRepository.save(ElderProfile.builder()
                .familyUserId(family.getId()).elderUserId(elder.getId()).fullName("Still Here").build());
        entityManager.flush();

        userRepository.delete(elder);
        entityManager.flush();
        entityManager.clear();

        ElderProfile reloaded = elderProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(reloaded.getElderUserId()).isNull();
        assertThat(reloaded.getFamilyUserId()).isEqualTo(family.getId());
    }
}
