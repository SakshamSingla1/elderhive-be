package com.elderhive.services;

import com.elderhive.entities.RefreshToken;
import com.elderhive.entities.User;

import java.util.Optional;

public interface RefreshTokenService {

    /** Generates, persists (hashed), and returns the RAW token — the only time it's
     *  ever in memory un-hashed, never persisted in this form. */
    String issue(User user);

    /** Hashes rawToken and looks it up; present only if found, not revoked, and not
     *  expired — a single call so a caller (e.g. the refresh endpoint) gets the
     *  associated userId without a second lookup. */
    Optional<RefreshToken> validate(String rawToken);

    void revoke(String rawToken);

    void revokeAllForUser(Long userId);
}
