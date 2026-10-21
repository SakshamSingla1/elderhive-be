package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.FavoriteCaretaker;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V23's migration matches the entity
 * mapping, both FKs' CASCADE behavior, the (family_user_id, caretaker_id) unique
 * constraint, and JPA auditing.
 */
@SpringBootTest
@Transactional
class FavoriteCaretakerRepositoryTest {

    @Autowired
    private FavoriteCaretakerRepository favoriteCaretakerRepository;

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum type) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build());
    }

    private CaretakerProfile persistCaretakerProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
    }

    @Test
    void persistingAndReloadingAFavoritePopulatesAuditFieldsAndEveryColumn() {
        User family = persistUser("fc-roundtrip-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-roundtrip-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile profile = persistCaretakerProfile(caretakerUser.getId());

        FavoriteCaretaker saved = favoriteCaretakerRepository.save(FavoriteCaretaker.builder()
                .familyUserId(family.getId()).caretakerId(profile.getId()).build());
        entityManager.flush();
        entityManager.clear();

        FavoriteCaretaker reloaded = favoriteCaretakerRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFamilyUserId()).isEqualTo(family.getId());
        assertThat(reloaded.getCaretakerId()).isEqualTo(profile.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void theSamePairCannotBeFavoritedTwice() {
        User family = persistUser("fc-unique-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-unique-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile profile = persistCaretakerProfile(caretakerUser.getId());
        favoriteCaretakerRepository.save(FavoriteCaretaker.builder().familyUserId(family.getId()).caretakerId(profile.getId()).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            favoriteCaretakerRepository.save(FavoriteCaretaker.builder().familyUserId(family.getId()).caretakerId(profile.getId()).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsFindAndDeleteByFamilyUserIdAndCaretakerIdWork() {
        User family = persistUser("fc-exists-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-exists-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile profile = persistCaretakerProfile(caretakerUser.getId());
        favoriteCaretakerRepository.save(FavoriteCaretaker.builder().familyUserId(family.getId()).caretakerId(profile.getId()).build());
        entityManager.flush();

        assertThat(favoriteCaretakerRepository.existsByFamilyUserIdAndCaretakerId(family.getId(), profile.getId())).isTrue();
        assertThat(favoriteCaretakerRepository.findByFamilyUserIdAndCaretakerId(family.getId(), profile.getId())).isPresent();

        favoriteCaretakerRepository.deleteByFamilyUserIdAndCaretakerId(family.getId(), profile.getId());
        entityManager.flush();

        assertThat(favoriteCaretakerRepository.existsByFamilyUserIdAndCaretakerId(family.getId(), profile.getId())).isFalse();
    }

    @Test
    void findByFamilyUserIdOrderByCreatedAtDescReturnsOnlyThatFamilyUsersFavorites() {
        User family1 = persistUser("fc-find1-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User family2 = persistUser("fc-find2-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser1 = persistUser("fc-find1-caretaker@example.com", UserTypeEnum.CARETAKER);
        User caretakerUser2 = persistUser("fc-find2-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile profile1 = persistCaretakerProfile(caretakerUser1.getId());
        CaretakerProfile profile2 = persistCaretakerProfile(caretakerUser2.getId());
        favoriteCaretakerRepository.save(FavoriteCaretaker.builder().familyUserId(family1.getId()).caretakerId(profile1.getId()).build());
        favoriteCaretakerRepository.save(FavoriteCaretaker.builder().familyUserId(family1.getId()).caretakerId(profile2.getId()).build());
        favoriteCaretakerRepository.save(FavoriteCaretaker.builder().familyUserId(family2.getId()).caretakerId(profile1.getId()).build());
        entityManager.flush();

        List<FavoriteCaretaker> favorites = favoriteCaretakerRepository.findByFamilyUserIdOrderByCreatedAtDesc(family1.getId());

        assertThat(favorites).hasSize(2);
        assertThat(favorites).extracting(FavoriteCaretaker::getFamilyUserId).containsOnly(family1.getId());
    }

    @Test
    void deletingTheFamilyUserCascadesToTheirFavorites() {
        User family = persistUser("fc-cascade-family-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-cascade-family-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile profile = persistCaretakerProfile(caretakerUser.getId());
        FavoriteCaretaker saved = favoriteCaretakerRepository.save(
                FavoriteCaretaker.builder().familyUserId(family.getId()).caretakerId(profile.getId()).build());
        entityManager.flush();

        userRepository.delete(family);
        entityManager.flush();
        entityManager.clear();

        assertThat(favoriteCaretakerRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheCaretakerProfileCascadesToFavoritesOfIt() {
        User family = persistUser("fc-cascade-caretaker-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-cascade-caretaker-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile profile = persistCaretakerProfile(caretakerUser.getId());
        FavoriteCaretaker saved = favoriteCaretakerRepository.save(
                FavoriteCaretaker.builder().familyUserId(family.getId()).caretakerId(profile.getId()).build());
        entityManager.flush();

        caretakerProfileRepository.delete(profile);
        entityManager.flush();
        entityManager.clear();

        assertThat(favoriteCaretakerRepository.findById(saved.getId())).isEmpty();
    }
}
