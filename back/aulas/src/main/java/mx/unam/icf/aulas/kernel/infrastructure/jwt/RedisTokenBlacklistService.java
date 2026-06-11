package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed implementation of {@link TokenBlacklist} designed for production environments.
 * <p>
 * This service uses Redis via {@link StringRedisTemplate} to ensure that token invalidation
 * is synchronized across multiple stateless instances of the application.
 * </p>
 */
@Service
@Profile("prod")
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklist {

    /**
     * Namespace prefix for keys stored in Redis to prevent collisions.
     */
    private static final String PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redis;

    /**
     * Persists the token identifier into Redis with an explicit expiration time.
     *
     * @param jti        the unique JWT ID claim to invalidate
     * @param ttlSeconds the expiration time in seconds for the Redis key
     */
    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds > 0)
            redis.opsForValue().set(PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Queries Redis to see if the token identifier key exists.
     *
     * @param jti the unique JWT ID claim to check
     * @return {@code true} if the key exists in Redis, {@code false} otherwise
     */
    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}