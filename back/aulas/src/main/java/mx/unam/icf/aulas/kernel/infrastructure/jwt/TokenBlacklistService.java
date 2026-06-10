package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed store for revoked JWT token IDs (JTI).
 *
 * <p>When a user logs out, rotates a refresh token, or resets a password, the token's
 * JTI is written to Redis with the same TTL as the token itself. Any subsequent request
 * that presents a blacklisted JTI is rejected with 401, even if the token signature
 * and expiry are otherwise valid.</p>
 *
 * <p>Keys are namespaced under {@code jwt:blacklist:<jti>} to avoid collisions with
 * other Redis data in the same instance.</p>
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redis;

    /**
     * Adds the given JTI to the blacklist with the specified TTL.
     * No-op when {@code ttlSeconds} is zero or negative (already expired tokens need no entry).
     *
     * @param jti        the unique token ID to revoke
     * @param ttlSeconds remaining lifetime of the token in seconds
     */
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds > 0)
            redis.opsForValue().set(PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Returns {@code true} if the given JTI is present in the blacklist (i.e., has been revoked).
     *
     * @param jti the unique token ID to check
     * @return {@code true} if the token has been revoked
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
