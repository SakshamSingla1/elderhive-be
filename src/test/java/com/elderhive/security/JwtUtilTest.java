package com.elderhive.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() {
        // @Value-injected in production; set directly here since this is a plain unit test
        // with no Spring context.
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "unit-test-only-secret-at-least-32-bytes-long");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 900_000L);
    }

    @Test
    void generatesATokenThatRoundTripsAllClaims() {
        String token = jwtUtil.generateAccessToken("elder@example.com", 42L, "ELDER");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("elder@example.com");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ELDER");
    }

    @Test
    void validatesATokenAgainstTheEmailItWasIssuedFor() {
        String token = jwtUtil.generateAccessToken("elder@example.com", 42L, "ELDER");

        assertThat(jwtUtil.validateToken(token, "elder@example.com")).isTrue();
        assertThat(jwtUtil.validateToken(token, "someone-else@example.com")).isFalse();
    }

    @Test
    void rejectsAnExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", -1_000L);
        String expiredToken = jwtUtil.generateAccessToken("elder@example.com", 42L, "ELDER");

        assertThat(jwtUtil.validateToken(expiredToken, "elder@example.com")).isFalse();
    }

    @Test
    void rejectsAMalformedToken() {
        assertThat(jwtUtil.validateToken("not-a-real-jwt", "elder@example.com")).isFalse();
    }
}
