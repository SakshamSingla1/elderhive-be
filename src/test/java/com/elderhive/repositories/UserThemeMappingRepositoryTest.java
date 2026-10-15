package com.elderhive.repositories;

import com.elderhive.entities.ColorTheme;
import com.elderhive.entities.User;
import com.elderhive.entities.UserThemeMapping;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres (docker-compose.yml) — confirms the unique
 * constraint on user_id, both FK relationships (including their ON DELETE CASCADE
 * behavior), and every derived query method actually work against a live schema, not just
 * that the entity compiles and Hibernate's ddl-auto=validate accepts the mapping.
 */
@SpringBootTest
@Transactional
class UserThemeMappingRepositoryTest {

    @Autowired
    private UserThemeMappingRepository userThemeMappingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager; // no ColorThemeRepository yet (Prompt 42)

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .userType(UserTypeEnum.ELDER)
                .status(UserStatusEnum.ACTIVE)
                .build());
    }

    private ColorTheme persistTheme(String name) {
        ColorTheme theme = ColorTheme.builder().themeName(name).status(ColorThemeStatusEnum.ACTIVE).build();
        entityManager.persist(theme);
        entityManager.flush();
        return theme;
    }

    @Test
    void savesAndFindsAMappingByUserId() {
        User user = persistUser("theme-user@example.com");
        ColorTheme theme = persistTheme("Ocean Blue");

        UserThemeMapping mapping = userThemeMappingRepository.save(
                UserThemeMapping.builder().userId(user.getId()).themeId(theme.getId()).build());

        assertThat(mapping.getId()).isNotNull();
        assertThat(mapping.getCreatedAt()).isNotNull();
        assertThat(userThemeMappingRepository.findByUserId(user.getId())).contains(mapping);
    }

    @Test
    void findByUserIdIsEmptyWhenNoMappingExists_absenceMeansDefaultTheme() {
        User user = persistUser("no-mapping-user@example.com");

        assertThat(userThemeMappingRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void existsByUserIdReflectsWhetherAMappingExists() {
        User user = persistUser("exists-user@example.com");
        ColorTheme theme = persistTheme("Sunset Orange");

        assertThat(userThemeMappingRepository.existsByUserId(user.getId())).isFalse();

        userThemeMappingRepository.save(UserThemeMapping.builder().userId(user.getId()).themeId(theme.getId()).build());

        assertThat(userThemeMappingRepository.existsByUserId(user.getId())).isTrue();
    }

    @Test
    void deleteByUserIdRemovesTheMapping() {
        User user = persistUser("delete-user@example.com");
        ColorTheme theme = persistTheme("Deletable Theme");
        userThemeMappingRepository.save(UserThemeMapping.builder().userId(user.getId()).themeId(theme.getId()).build());

        userThemeMappingRepository.deleteByUserId(user.getId());

        assertThat(userThemeMappingRepository.existsByUserId(user.getId())).isFalse();
    }

    @Test
    void findAllByThemeIdAndCountByThemeIdReflectEveryUserOnThatTheme() {
        ColorTheme sharedTheme = persistTheme("Shared Theme");
        ColorTheme otherTheme = persistTheme("Other Theme");
        User userA = persistUser("shared-a@example.com");
        User userB = persistUser("shared-b@example.com");
        User userC = persistUser("other-user@example.com");

        userThemeMappingRepository.save(UserThemeMapping.builder().userId(userA.getId()).themeId(sharedTheme.getId()).build());
        userThemeMappingRepository.save(UserThemeMapping.builder().userId(userB.getId()).themeId(sharedTheme.getId()).build());
        userThemeMappingRepository.save(UserThemeMapping.builder().userId(userC.getId()).themeId(otherTheme.getId()).build());

        assertThat(userThemeMappingRepository.findAllByThemeId(sharedTheme.getId()))
                .extracting(UserThemeMapping::getUserId)
                .containsExactlyInAnyOrder(userA.getId(), userB.getId());
        assertThat(userThemeMappingRepository.countByThemeId(sharedTheme.getId())).isEqualTo(2L);
        assertThat(userThemeMappingRepository.countByThemeId(otherTheme.getId())).isEqualTo(1L);
    }

    @Test
    void rejectsASecondMappingForTheSameUser() {
        User user = persistUser("one-mapping-user@example.com");
        ColorTheme themeOne = persistTheme("First Theme");
        ColorTheme themeTwo = persistTheme("Second Theme");
        userThemeMappingRepository.save(UserThemeMapping.builder().userId(user.getId()).themeId(themeOne.getId()).build());

        assertThatThrownBy(() -> {
            userThemeMappingRepository.save(UserThemeMapping.builder().userId(user.getId()).themeId(themeTwo.getId()).build());
            userThemeMappingRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheReferencedUserCascadesToItsMapping() {
        User user = persistUser("cascade-user@example.com");
        ColorTheme theme = persistTheme("Cascade Theme");
        userThemeMappingRepository.save(UserThemeMapping.builder().userId(user.getId()).themeId(theme.getId()).build());
        userThemeMappingRepository.flush();

        userRepository.delete(user);
        userRepository.flush();

        assertThat(userThemeMappingRepository.existsByUserId(user.getId())).isFalse();
    }

    @Test
    void deletingTheReferencedThemeCascadesToItsMappings_userFallsBackToTheDefaultTheme() {
        User user = persistUser("theme-cascade-user@example.com");
        ColorTheme theme = persistTheme("Soon Deleted Theme");
        userThemeMappingRepository.save(UserThemeMapping.builder().userId(user.getId()).themeId(theme.getId()).build());
        userThemeMappingRepository.flush();

        entityManager.remove(entityManager.find(ColorTheme.class, theme.getId()));
        entityManager.flush();

        // Mapping is gone, not left dangling - this user now resolves to the default
        // theme per this entity's own "absence means default" contract.
        assertThat(userThemeMappingRepository.existsByUserId(user.getId())).isFalse();
    }
}
