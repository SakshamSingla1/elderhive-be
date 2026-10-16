package com.elderhive.repositories;

import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ElderProfileLinkInvite;
import com.elderhive.entities.User;
import com.elderhive.enums.LinkInviteStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V19's migration matches the entity
 * mapping, the elder_profile cascade, the token unique constraint, and JPA auditing.
 */
@SpringBootTest
@Transactional
class ElderProfileLinkInviteRepositoryTest {

    @Autowired
    private ElderProfileLinkInviteRepository inviteRepository;

    @Autowired
    private ElderProfileRepository elderProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.FAMILY_MEMBER).status(UserStatusEnum.ACTIVE).build());
    }

    private ElderProfile persistProfile(Long familyUserId) {
        return elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
    }

    private ElderProfileLinkInvite invite(Long elderProfileId, String email, String token) {
        return ElderProfileLinkInvite.builder()
                .elderProfileId(elderProfileId).invitedEmail(email).invitedRole(UserTypeEnum.FAMILY_MEMBER)
                .relationshipLabel("Son").status(LinkInviteStatusEnum.PENDING).token(token).build();
    }

    @Test
    void persistingAndReloadingAnInvitePopulatesAuditFieldsAndEveryColumn() {
        User owner = persistUser("invite-roundtrip-owner@example.com");
        ElderProfile profile = persistProfile(owner.getId());

        ElderProfileLinkInvite saved = inviteRepository.save(
                invite(profile.getId(), "invitee@example.com", UUID.randomUUID().toString()));
        entityManager.flush();
        entityManager.clear();

        ElderProfileLinkInvite reloaded = inviteRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getElderProfileId()).isEqualTo(profile.getId());
        assertThat(reloaded.getInvitedEmail()).isEqualTo("invitee@example.com");
        assertThat(reloaded.getInvitedRole()).isEqualTo(UserTypeEnum.FAMILY_MEMBER);
        assertThat(reloaded.getStatus()).isEqualTo(LinkInviteStatusEnum.PENDING);
        assertThat(reloaded.getToken()).isNotBlank();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByTokenReturnsTheMatchingInvite() {
        User owner = persistUser("invite-bytoken-owner@example.com");
        ElderProfile profile = persistProfile(owner.getId());
        String token = UUID.randomUUID().toString();
        inviteRepository.save(invite(profile.getId(), "invitee2@example.com", token));
        entityManager.flush();

        assertThat(inviteRepository.findByToken(token)).isPresent();
        assertThat(inviteRepository.findByToken("not-a-real-token")).isEmpty();
    }

    @Test
    void theTokenColumnIsGloballyUnique() {
        User owner = persistUser("invite-unique-owner@example.com");
        ElderProfile profile1 = persistProfile(owner.getId());
        ElderProfile profile2 = persistProfile(owner.getId());
        String sharedToken = UUID.randomUUID().toString();
        inviteRepository.save(invite(profile1.getId(), "a@example.com", sharedToken));
        entityManager.flush();

        assertThatThrownBy(() -> {
            inviteRepository.save(invite(profile2.getId(), "b@example.com", sharedToken));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByElderProfileIdOrderByCreatedAtDescReturnsOnlyInvitesForThatProfile() {
        User owner = persistUser("invite-find-owner@example.com");
        ElderProfile profile = persistProfile(owner.getId());
        ElderProfile otherProfile = persistProfile(owner.getId());
        inviteRepository.save(invite(profile.getId(), "p1a@example.com", UUID.randomUUID().toString()));
        inviteRepository.save(invite(profile.getId(), "p1b@example.com", UUID.randomUUID().toString()));
        inviteRepository.save(invite(otherProfile.getId(), "p2a@example.com", UUID.randomUUID().toString()));
        entityManager.flush();

        List<ElderProfileLinkInvite> invites = inviteRepository.findByElderProfileIdOrderByCreatedAtDesc(profile.getId());

        assertThat(invites).hasSize(2);
        assertThat(invites).extracting(ElderProfileLinkInvite::getInvitedEmail)
                .containsExactlyInAnyOrder("p1a@example.com", "p1b@example.com");
    }

    @Test
    void existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatusMatchesOnlyPendingInvitesForThatContact() {
        User owner = persistUser("invite-exists-owner@example.com");
        ElderProfile profile = persistProfile(owner.getId());
        inviteRepository.save(invite(profile.getId(), "exists@example.com", UUID.randomUUID().toString()));
        entityManager.flush();

        assertThat(inviteRepository.existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
                profile.getId(), "exists@example.com", UserTypeEnum.FAMILY_MEMBER, LinkInviteStatusEnum.PENDING)).isTrue();
        assertThat(inviteRepository.existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
                profile.getId(), "exists@example.com", UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)).isFalse();
        assertThat(inviteRepository.existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
                profile.getId(), "exists@example.com", UserTypeEnum.FAMILY_MEMBER, LinkInviteStatusEnum.ACCEPTED)).isFalse();
    }

    @Test
    void deletingTheElderProfileCascadesToItsInvites() {
        User owner = persistUser("invite-cascade-owner@example.com");
        ElderProfile profile = persistProfile(owner.getId());
        ElderProfileLinkInvite saved = inviteRepository.save(
                invite(profile.getId(), "cascade@example.com", UUID.randomUUID().toString()));
        entityManager.flush();

        elderProfileRepository.delete(profile);
        entityManager.flush();
        entityManager.clear();

        assertThat(inviteRepository.findById(saved.getId())).isEmpty();
    }
}
