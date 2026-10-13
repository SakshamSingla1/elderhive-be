package com.elderhive.services;

import com.elderhive.entities.RefreshToken;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RefreshTokenRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres (docker-compose.yml) — proves the full issue /
 * validate / revoke / revokeAllForUser lifecycle actually works, and specifically that
 * the raw token is never what's persisted (only its SHA-256 hash is).
 */
@SpringBootTest
@Transactional
class RefreshTokenServiceImplTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .userType(UserTypeEnum.FAMILY_MEMBER)
                .status(UserStatusEnum.ACTIVE)
                .build());
    }

    private static String sha256Hex(String raw) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }

    @Test
    void issuedTokenValidatesAndOnlyItsHashIsPersisted() throws Exception {
        User user = persistUser("refresh-issue@example.com");

        String rawToken = refreshTokenService.issue(user);
        assertThat(rawToken).isNotBlank();

        // the raw token itself is nowhere in the table - only its hash
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .doesNotContain(rawToken)
                .contains(sha256Hex(rawToken));

        Optional<RefreshToken> validated = refreshTokenService.validate(rawToken);
        assertThat(validated).isPresent();
        assertThat(validated.get().getUserId()).isEqualTo(user.getId());
        assertThat(validated.get().isRevoked()).isFalse();
    }

    @Test
    void validateRejectsAnUnknownToken() {
        assertThat(refreshTokenService.validate("not-a-real-token")).isEmpty();
    }

    @Test
    void revokeInvalidatesTheToken() {
        User user = persistUser("refresh-revoke@example.com");
        String rawToken = refreshTokenService.issue(user);

        refreshTokenService.revoke(rawToken);

        assertThat(refreshTokenService.validate(rawToken)).isEmpty();
    }

    @Test
    void revokeAllForUserInvalidatesEveryTokenForThatUserOnly() {
        User user = persistUser("refresh-revoke-all@example.com");
        User otherUser = persistUser("refresh-other@example.com");

        String tokenA = refreshTokenService.issue(user);
        String tokenB = refreshTokenService.issue(user);
        String otherUsersToken = refreshTokenService.issue(otherUser);

        refreshTokenService.revokeAllForUser(user.getId());

        assertThat(refreshTokenService.validate(tokenA)).isEmpty();
        assertThat(refreshTokenService.validate(tokenB)).isEmpty();
        assertThat(refreshTokenService.validate(otherUsersToken)).isPresent();
    }
}
