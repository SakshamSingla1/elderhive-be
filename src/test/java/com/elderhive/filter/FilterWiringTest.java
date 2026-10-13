package com.elderhive.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtAuthFilter;
import com.elderhive.security.JwtUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the two things Prompt 14 actually requires beyond "the filters exist":
 * that they're registered in the right relative order, and that each one's observable
 * behavior (429 after the bucket empties, userId appearing in the audit line) is real,
 * not just plausible-looking code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FilterWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    private static int indexOfType(List<Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) return i;
        }
        return -1;
    }

    @Test
    void filtersAreRegisteredInTheOrderThePromptAsksFor() {
        // Read the ACTUAL registered chain rather than inferring order from side
        // effects — this is the authoritative source of truth for what Spring
        // Security will really do at request time.
        SecurityFilterChain chain = filterChainProxy.getFilterChains().get(0);
        List<Filter> filters = chain.getFilters();

        int rateLimitIdx = indexOfType(filters, RateLimitingFilter.class);
        int jwtAuthIdx = indexOfType(filters, JwtAuthFilter.class);
        int auditIdx = indexOfType(filters, AuditFilter.class);

        assertThat(rateLimitIdx).as("RateLimitingFilter registered").isGreaterThanOrEqualTo(0);
        assertThat(jwtAuthIdx).as("JwtAuthFilter registered").isGreaterThanOrEqualTo(0);
        assertThat(auditIdx).as("AuditFilter registered").isGreaterThanOrEqualTo(0);

        assertThat(rateLimitIdx).as("rate limiting before JWT auth").isLessThan(jwtAuthIdx);
        assertThat(auditIdx).as("audit filter after JWT auth").isGreaterThan(jwtAuthIdx);
    }

    @Test
    void authPathsAreRateLimitedPerClientIp() throws Exception {
        // A synthetic X-Forwarded-For isolates this test's bucket from whatever other
        // tests in the suite also happen to hit /auth/** under MockMvc's real default
        // remote address (127.0.0.1, which is a trusted proxy, so this header is honored).
        String syntheticIp = "203.0.113.55";
        String path = "/auth/rate-limit-isolated-test-path";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get(path).header("X-Forwarded-For", syntheticIp))
                    .andExpect(status().isNotFound()); // permitAll lets it through; no controller matches
        }

        mockMvc.perform(get(path).header("X-Forwarded-For", syntheticIp))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void auditLogIncludesUserIdWhenAuthenticated() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);

        try {
            User user = userRepository.save(User.builder()
                    .email("audit-filter-test@example.com")
                    .passwordHash("hashed")
                    .userType(UserTypeEnum.ADMIN)
                    .status(UserStatusEnum.ACTIVE)
                    .build());
            String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());

            mockMvc.perform(get("/actuator/health").cookie(new Cookie("accessToken", token)))
                    .andExpect(status().isOk());

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("GET /actuator/health -> 200")
                            .contains("userId=" + user.getId()));
        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    @Test
    void auditLogOmitsUserIdWhenNotAuthenticated() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);

        try {
            mockMvc.perform(get("/v3/api-docs"));

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("GET /v3/api-docs")
                            .doesNotContain("userId="));
        } finally {
            auditLogger.detachAppender(appender);
        }
    }
}
