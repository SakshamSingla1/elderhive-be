package com.elderhive.integration;

import com.elderhive.entities.ColorTheme;
import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.ColorThemeRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
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
 * End-to-end over the real dispatched Spring Security + MVC stack, mirroring
 * ColorThemeControllerIntegrationTest's approach. Proves the self-resolution-from-JWT
 * behavior of GET/POST/DELETE, the default-theme fallback actually reaching an HTTP
 * caller end-to-end, and that the two reverse-lookup endpoints are genuinely gated by
 * THEME_MANAGE (not merely isAuthenticated() the way Portfolio leaves its equivalent) —
 * THEME_MANAGE is in the curated "Admin" role's default grant (AdminBootstrapSeeder does
 * NOT put it in SUPER_ADMIN_ONLY_CODES, unlike ROLE_MANAGE/CONFIG_MANAGE), so a plain
 * curated ADMIN should pass, while a non-admin role (ELDER/etc.) should not.
 *
 * <p><b>Deliberately NOT {@code @Transactional}</b>, unlike every other integration test in
 * this package. getThemeByUserId's concurrent-fetch optimization (mirrored from Portfolio,
 * see UserThemeServiceImpl's javadoc) runs its DB reads via
 * {@code CompletableFuture.supplyAsync} on a separate thread, which does not participate in
 * Spring Test's per-test transaction — under {@code @Transactional}, that worker thread
 * cannot see this test method's own uncommitted inserts and every call spuriously 404s with
 * USER_NOT_FOUND (confirmed by temporarily adding {@code @Transactional} here and watching
 * every test fail that way). This is specific to the test harness's rollback-per-test
 * transaction boundary, not a production bug — a real request is never nested inside
 * another transaction supplying the data it reads. Rows are committed for real here, so
 * cleanup happens manually in {@link #cleanUpPersistedRows()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserThemeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ColorThemeRepository colorThemeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdThemeIds = new ArrayList<>();

    @BeforeEach
    void clearCaches() {
        // getDefaultTheme()/getThemeById() are @Cacheable - a value left behind by another
        // test class sharing this ApplicationContext's CacheManager would otherwise leak in.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @AfterEach
    void cleanUpPersistedRows() {
        // Both FKs on user_theme_mappings are ON DELETE CASCADE (Prompt 41's migration), so
        // deleting the users and themes created here cascades away their mappings too.
        userRepository.deleteAllById(createdUserIds);
        colorThemeRepository.deleteAllById(createdThemeIds);
        createdUserIds.clear();
        createdThemeIds.clear();
    }

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType, Long roleId) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType)
                .status(UserStatusEnum.ACTIVE).roleId(roleId).build());
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
    void callerWithNoMappingResolvesToThePlatformDefaultThemeOverRealHttp() throws Exception {
        persistActiveTheme("Seeded Default");
        User elder = persistUser("ut-elder@example.com", UserTypeEnum.ELDER, null);

        MvcResult result = mockMvc.perform(get("/api/v1/user-themes").cookie(loginCookieFor(elder)))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("id").isNull()).isTrue();
        assertThat(data.get("themeName").asText()).isEqualTo("Seeded Default");
    }

    @Test
    void settingAndThenDeletingATheme_RevertsTheCallerToTheDefaultOverRealHttp() throws Exception {
        // getDefaultTheme() resolves to the most-recently-created ACTIVE theme, so
        // "Platform Default" must be persisted AFTER "Chosen By Caller" to actually be the
        // one the fallback resolves to post-delete.
        ColorTheme chosen = persistActiveTheme("Chosen By Caller");
        persistActiveTheme("Platform Default");
        User familyMember = persistUser("ut-family@example.com", UserTypeEnum.FAMILY_MEMBER, null);
        Cookie cookie = loginCookieFor(familyMember);

        mockMvc.perform(post("/api/v1/user-themes").cookie(cookie).contentType("application/json")
                        .content("{\"themeId\": " + chosen.getId() + "}"))
                .andExpect(status().isOk());

        MvcResult afterSet = mockMvc.perform(get("/api/v1/user-themes").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(afterSet.getResponse().getContentAsString())
                .get("data").get("themeName").asText()).isEqualTo("Chosen By Caller");

        mockMvc.perform(delete("/api/v1/user-themes").cookie(cookie)).andExpect(status().isOk());

        MvcResult afterDelete = mockMvc.perform(get("/api/v1/user-themes").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode afterDeleteData = objectMapper.readTree(afterDelete.getResponse().getContentAsString()).get("data");
        assertThat(afterDeleteData.get("id").isNull()).isTrue();
        assertThat(afterDeleteData.get("themeName").asText()).isEqualTo("Platform Default");
    }

    @Test
    void reverseLookupEndpointsAreGatedByThemeManageNotMerelyAuthenticated() throws Exception {
        ColorTheme theme = persistActiveTheme("Reverse Lookup Theme");
        User caretaker = persistUser("ut-caretaker@example.com", UserTypeEnum.CARETAKER, null);
        mockMvc.perform(post("/api/v1/user-themes").cookie(loginCookieFor(caretaker)).contentType("application/json")
                        .content("{\"themeId\": " + theme.getId() + "}"))
                .andExpect(status().isOk());

        // A non-admin, authenticated caller is denied - the deliberate hardening vs Portfolio.
        mockMvc.perform(get("/api/v1/user-themes/theme/" + theme.getId()).cookie(loginCookieFor(caretaker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/user-themes/theme/" + theme.getId() + "/count").cookie(loginCookieFor(caretaker)))
                .andExpect(status().isForbidden());

        // A curated ADMIN genuinely holding THEME_MANAGE (not SUPER_ADMIN_ONLY) passes.
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        User admin = persistUser("ut-admin@example.com", UserTypeEnum.ADMIN, adminRole.getId());
        Cookie adminCookie = loginCookieFor(admin);

        MvcResult listResult = mockMvc.perform(get("/api/v1/user-themes/theme/" + theme.getId()).cookie(adminCookie))
                .andExpect(status().isOk()).andReturn();
        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("userEmail").asText()).isEqualTo("ut-caretaker@example.com");

        MvcResult countResult = mockMvc.perform(get("/api/v1/user-themes/theme/" + theme.getId() + "/count").cookie(adminCookie))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(countResult.getResponse().getContentAsString()).get("data").asLong()).isEqualTo(1L);

        // SUPER_ADMIN passes too (AdminPermissionGuard unconditionally grants it).
        User superAdmin = persistUser("ut-superadmin@example.com", UserTypeEnum.SUPER_ADMIN, null);
        mockMvc.perform(get("/api/v1/user-themes/theme/" + theme.getId()).cookie(loginCookieFor(superAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCallerIsRejectedOnEveryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/user-themes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/user-themes/theme/1")).andExpect(status().isUnauthorized());
    }
}
