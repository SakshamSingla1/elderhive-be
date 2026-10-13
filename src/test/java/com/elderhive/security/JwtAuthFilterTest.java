package com.elderhive.security;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Runs against the real local Postgres (docker-compose.yml) and real JwtUtil/
 * CustomUserDetailsService beans — proves the whole cookie -> JWT -> SecurityContext ->
 * "currentUserId" request attribute chain actually works together, which is exactly
 * what Prompt 8's AuditorAwareImpl will depend on once SecurityConfig wires this filter
 * into the chain (the next prompt).
 */
@SpringBootTest
@Transactional
class JwtAuthFilterTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCookieAuthenticatesAndSetsCurrentUserIdAttribute() throws Exception {
        User user = userRepository.save(User.builder()
                .email("elder-filter-test@example.com")
                .passwordHash("hashed")
                .userType(UserTypeEnum.ELDER)
                .status(UserStatusEnum.ACTIVE)
                .build());

        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());

        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, customUserDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("accessToken", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(user.getEmail());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ELDER");
        assertThat(request.getAttribute("currentUserId")).isEqualTo(user.getId().toString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void noCookieLeavesRequestUnauthenticated() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, customUserDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("currentUserId")).isNull();
        verify(chain).doFilter(request, response);
    }
}
