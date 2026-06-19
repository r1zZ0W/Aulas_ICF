package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * In-memory implementation of {@link TokenBlacklist} backed by Caffeine via Spring's
 * {@link CacheManager} abstraction.
 * <p>
 * Blacklisted identifiers live in the application heap and are automatically evicted
 * once the corresponding JWT naturally expires.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InMemoryTokenBlacklistService implements TokenBlacklist {

    private final CacheManager cacheManager;

    /**
     * Registers the token identifier in the local cache if the TTL is valid.
     *
     * @param jti        the unique JWT ID claim to invalidate
     * @param ttlSeconds the duration in seconds before the entry can be evicted
     */
    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds > 0)
            blacklistCache().put(jti, true);
    }

    /**
     * Verifies if the token identifier exists in the local cache.
     *
     * @param jti the unique JWT ID claim to check
     * @return {@code true} if present in the cache, {@code false} otherwise
     */
    @Override
    public boolean isBlacklisted(String jti) {
        return blacklistCache().get(jti) != null;
    }

    /**
     * Resolves the specialized "token-blacklist" cache instance.
     *
     * @return the configured {@link Cache} instance
     */
    private Cache blacklistCache() {
        return cacheManager.getCache("token-blacklist");
    }
}
