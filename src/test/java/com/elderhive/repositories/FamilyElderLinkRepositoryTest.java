package com.elderhive.repositories;

import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V18's migration matches the entity
 * mapping, both FKs' CASCADE behavior, the (elder_profile_id, family_user_id) unique
 * constraint, and JPA auditing.
 */
@SpringBootTest
@Transactional
class FamilyElderLinkRepositoryTest {

    @Autowired
    private FamilyElderLinkRepository familyElderLinkRepository;

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

    private ElderProfile persistProfile(Long familyUserId) {
        return elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
    }

    @Test
    void persistingAndReloadingALinkPopulatesAuditFieldsAndEveryColumn() {
        User owner = persistUser("fel-roundtrip-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-roundtrip-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = persistProfile(owner.getId());

        FamilyElderLink saved = familyElderLinkRepository.save(FamilyElderLink.builder()
                .elderProfileId(profile.getId()).familyUserId(coManager.getId()).relationshipLabel("Daughter").build());
        entityManager.flush();
        entityManager.clear();

        FamilyElderLink reloaded = familyElderLinkRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getElderProfileId()).isEqualTo(profile.getId());
        assertThat(reloaded.getFamilyUserId()).isEqualTo(coManager.getId());
        assertThat(reloaded.getRelationshipLabel()).isEqualTo("Daughter");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void theSameFamilyMemberCannotBeLinkedToTheSameProfileTwice() {
        User owner = persistUser("fel-unique-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-unique-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = persistProfile(owner.getId());
        familyElderLinkRepository.save(FamilyElderLink.builder()
                .elderProfileId(profile.getId()).familyUserId(coManager.getId()).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            familyElderLinkRepository.save(FamilyElderLink.builder()
                    .elderProfileId(profile.getId()).familyUserId(coManager.getId()).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByElderProfileIdReturnsOnlyLinksForThatProfile() {
        User owner = persistUser("fel-find-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager1 = persistUser("fel-find-comanager1@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager2 = persistUser("fel-find-comanager2@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = persistProfile(owner.getId());
        ElderProfile otherProfile = persistProfile(owner.getId());
        familyElderLinkRepository.save(FamilyElderLink.builder().elderProfileId(profile.getId()).familyUserId(coManager1.getId()).build());
        familyElderLinkRepository.save(FamilyElderLink.builder().elderProfileId(profile.getId()).familyUserId(coManager2.getId()).build());
        familyElderLinkRepository.save(FamilyElderLink.builder().elderProfileId(otherProfile.getId()).familyUserId(coManager1.getId()).build());
        entityManager.flush();

        List<FamilyElderLink> links = familyElderLinkRepository.findByElderProfileId(profile.getId());

        assertThat(links).hasSize(2);
        assertThat(links).extracting(FamilyElderLink::getFamilyUserId)
                .containsExactlyInAnyOrder(coManager1.getId(), coManager2.getId());
    }

    @Test
    void existsAndDeleteByElderProfileIdAndFamilyUserIdWork() {
        User owner = persistUser("fel-exists-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-exists-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = persistProfile(owner.getId());
        familyElderLinkRepository.save(FamilyElderLink.builder().elderProfileId(profile.getId()).familyUserId(coManager.getId()).build());
        entityManager.flush();

        assertThat(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(profile.getId(), coManager.getId())).isTrue();

        familyElderLinkRepository.deleteByElderProfileIdAndFamilyUserId(profile.getId(), coManager.getId());
        entityManager.flush();

        assertThat(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(profile.getId(), coManager.getId())).isFalse();
    }

    @Test
    void deletingTheElderProfileCascadesToItsLinks() {
        User owner = persistUser("fel-cascade-profile-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-cascade-profile-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = persistProfile(owner.getId());
        FamilyElderLink link = familyElderLinkRepository.save(FamilyElderLink.builder()
                .elderProfileId(profile.getId()).familyUserId(coManager.getId()).build());
        entityManager.flush();

        elderProfileRepository.delete(profile);
        entityManager.flush();
        entityManager.clear();

        assertThat(familyElderLinkRepository.findById(link.getId())).isEmpty();
    }

    @Test
    void deletingTheCoManagingFamilyMemberCascadesToTheirLink() {
        User owner = persistUser("fel-cascade-user-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-cascade-user-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile profile = persistProfile(owner.getId());
        FamilyElderLink link = familyElderLinkRepository.save(FamilyElderLink.builder()
                .elderProfileId(profile.getId()).familyUserId(coManager.getId()).build());
        entityManager.flush();

        userRepository.delete(coManager);
        entityManager.flush();
        entityManager.clear();

        assertThat(familyElderLinkRepository.findById(link.getId())).isEmpty();
    }
}
