package com.elderhive.integration;

import com.elderhive.entities.ColorTheme;
import com.elderhive.entities.User;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.ColorThemeRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.repositories.UserThemeMappingRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Closes the loop UserThemeMappingRepositoryTest's own
 * {@code deletingTheReferencedThemeCascadesToItsMappings_userFallsBackToTheDefaultTheme}
 * leaves open: that test proves the FK's {@code ON DELETE CASCADE} removes the orphaned
 * mapping row at the repository level, but never calls UserThemeService.getThemeByUserId
 * afterward to prove the AFFECTED USER actually gets a graceful response rather than an
 * error, and never goes through the real DELETE /api/v1/color-themes/:id endpoint (whose
 * {@code @CacheEvict}s are just as load-bearing here as the FK itself — see the second
 * test below).
 *
 * <p><b>The design decision this verifies</b>: deleting a ColorTheme that users are
 * actively on is allowed to proceed (not blocked) — the FK cascade silently removes their
 * UserThemeMapping row, and UserThemeService#getThemeByUserId's pre-existing "no mapping
 * means the default theme, not an error" contract (Prompt 44) then resolves them to
 * whatever the current default is. This was chosen over blocking the delete while in use:
 * nobody is left stuck on a 404/error, an admin pruning the catalog never needs to first
 * hunt down and reassign every affected user, and "falls back to default" is already this
 * project's own designed-in meaning of "no mapping" — reusing it for "mapping became
 * invalid" needs no new mechanism, just these two already-independently-built pieces
 * actually composing correctly, which is what this test proves.
 *
 * <p>Deliberately NOT {@code @Transactional}, same reasoning as
 * UserThemeControllerIntegrationTest: getThemeByUserId's concurrent-fetch optimization
 * runs its DB reads on a separate thread via CompletableFuture.supplyAsync, which can't
 * see this test method's own uncommitted inserts under a wrapping transaction. Rows are
 * committed for real; cleanup happens manually in {@link #cleanUpPersistedRows()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ColorThemeDeletionFallbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ColorThemeRepository colorThemeRepository;

    @Autowired
    private UserThemeMappingRepository userThemeMappingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdThemeIds = new ArrayList<>();

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @AfterEach
    void cleanUpPersistedRows() {
        userRepository.deleteAllById(createdUserIds);
        colorThemeRepository.deleteAllById(createdThemeIds);
        createdUserIds.clear();
        createdThemeIds.clear();
    }

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType)
                .status(UserStatusEnum.ACTIVE).build());
        createdUserIds.add(user.getId());
        return user;
    }

    private ColorTheme persistActiveTheme(String name) {
        ColorTheme theme = colorThemeRepository.save(
                ColorTheme.builder().themeName(name).status(ColorThemeStatusEnum.ACTIVE).build());
        createdThemeIds.add(theme.getId());
        return theme;
    }

    @Test
    void deletingAThemeAUserIsActivelyOnCascadesTheirMappingAndTheyFallBackToTheCurrentDefault() throws Exception {
        ColorTheme doomed = persistActiveTheme("Soon Deleted While In Use");
        ColorTheme currentDefault = persistActiveTheme("Current Default"); // created after -> wins by createdAt desc

        User superAdmin = persistUser("deletion-superadmin@example.com", UserTypeEnum.SUPER_ADMIN);
        User elder = persistUser("deletion-elder@example.com", UserTypeEnum.ELDER);
        Cookie superAdminCookie = loginCookieFor(superAdmin);
        Cookie elderCookie = loginCookieFor(elder);

        // Elder genuinely applies the doomed theme - not just a raw repository insert.
        mockMvc.perform(post("/api/v1/user-themes").cookie(elderCookie).contentType("application/json")
                        .content("{\"themeId\": " + doomed.getId() + "}"))
                .andExpect(status().isOk());

        MvcResult beforeDelete = mockMvc.perform(get("/api/v1/user-themes").cookie(elderCookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode beforeData = objectMapper.readTree(beforeDelete.getResponse().getContentAsString()).get("data");
        assertThat(beforeData.get("themeId").asLong()).isEqualTo(doomed.getId());
        assertThat(beforeData.get("id").isNull()).isFalse(); // a real, persisted mapping

        long mappingId = beforeData.get("id").asLong();
        assertThat(userThemeMappingRepository.existsById(mappingId)).isTrue();

        // The real admin endpoint, not a raw repository/entityManager delete - its
        // @CacheEvicts matter here just as much as the FK (see the next test).
        mockMvc.perform(delete("/api/v1/color-themes/" + doomed.getId()).cookie(superAdminCookie))
                .andExpect(status().isOk());

        assertThat(userThemeMappingRepository.existsById(mappingId))
                .as("the FK's ON DELETE CASCADE must have removed the now-orphaned mapping row")
                .isFalse();

        MvcResult afterDelete = mockMvc.perform(get("/api/v1/user-themes").cookie(elderCookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode afterData = objectMapper.readTree(afterDelete.getResponse().getContentAsString()).get("data");
        assertThat(afterData.get("id").isNull())
                .as("no mapping exists anymore, so the response must show the no-mapping shape, not an error")
                .isTrue();
        assertThat(afterData.get("themeId").asLong()).isEqualTo(currentDefault.getId());
        assertThat(afterData.get("themeName").asText()).isEqualTo("Current Default");
    }

    @Test
    void deletingTheThemeThatIsCurrentlyCachedAsTheDefaultStillResolvesToTheRealNewDefaultAfterward() throws Exception {
        ColorTheme soonToBeDefault = persistActiveTheme("Will Become The New Default");
        ColorTheme currentlyTheDefault = persistActiveTheme("Currently Cached As Default"); // newest -> today's default

        User superAdmin = persistUser("cache-deletion-superadmin@example.com", UserTypeEnum.SUPER_ADMIN);
        User elder = persistUser("cache-deletion-elder@example.com", UserTypeEnum.ELDER);
        Cookie superAdminCookie = loginCookieFor(superAdmin);
        Cookie elderCookie = loginCookieFor(elder);

        // Populate the colorThemeDefault cache with "Currently Cached As Default" BEFORE
        // deleting it - this is the exact case a stale cache would get wrong.
        MvcResult beforeDelete = mockMvc.perform(get("/api/v1/user-themes").cookie(elderCookie))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(beforeDelete.getResponse().getContentAsString())
                .get("data").get("themeName").asText()).isEqualTo("Currently Cached As Default");

        mockMvc.perform(delete("/api/v1/color-themes/" + currentlyTheDefault.getId()).cookie(superAdminCookie))
                .andExpect(status().isOk());

        MvcResult afterDelete = mockMvc.perform(get("/api/v1/user-themes").cookie(elderCookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode afterData = objectMapper.readTree(afterDelete.getResponse().getContentAsString()).get("data");
        assertThat(afterData.get("themeId").asLong())
                .as("colorThemeDefault must have been evicted, not left serving the now-deleted theme's stale id")
                .isEqualTo(soonToBeDefault.getId());
        assertThat(afterData.get("themeName").asText()).isEqualTo("Will Become The New Default");
    }
}
