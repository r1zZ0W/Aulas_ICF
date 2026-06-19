package mx.unam.icf.aulas.kernel.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the application.
 * <p>
 * This class enables Spring's annotation-driven cache management capability.
 * It configures environment-specific cache managers, such as an in-memory
 * Caffeine cache for non-production environments to handle token blacklisting.
 * </p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Configures a {@link CacheManager} backed by Caffeine for non-production environments.
     * <p>
     * This manager handles the "token-blacklist" cache with a dynamic Time-To-Live (TTL).
     * The eviction policy is derived from the JWT's own expiration claim, ensuring that
     * revoked tokens are kept in memory only for as long as they remain valid. Once a token
     * naturally expires, it is automatically evicted from the cache to free up memory.
     * </p>
     *
     * @param jwtExpirationMs fallback expiration time in milliseconds if the JWT parsing fails
     * @return a configured {@link CaffeineCacheManager} for token blacklisting
     */
    @Bean
    public CacheManager cacheManager(@Value("${jwt.expiration:3600000}") long jwtExpirationMs) {
        CaffeineCacheManager manager = new CaffeineCacheManager("token-blacklist");
        manager.setCaffeine(
            Caffeine.newBuilder().expireAfter(new Expiry<>() {
                @Override
                public long expireAfterCreate(@NonNull Object key, @NonNull Object value, long currentTime) {
                    long remaining = getRemainingTimeFromJwt((String) key, jwtExpirationMs);
                    return Math.max(remaining, 0L);
                }

                // A re-blacklist of the same token does not reset its expiry clock.
                @Override
                public long expireAfterUpdate(@NonNull Object key, @NonNull Object value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                // Reading the blacklist entry must not extend its lifetime.
                @Override
                public long expireAfterRead(@NonNull Object key, @NonNull Object value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
        );
        return manager;
    }

    /**
     * Parses the provided JWT token to calculate its remaining lifespan.
     * <p>
     * This method extracts the expiration millisecond timestamp (`exp` claim) from the token,
     * subtracts the current system time, and converts the resulting duration into nanoseconds
     * as required by Caffeine's {@link Expiry} interface.
     * If the token is invalid, malformed, or already expired, it safely returns 0 nanoseconds.
     * </p>
     *
     * @param token      the JWT string to parse
     * @param fallbackMs default duration to use if parsing encounters an unexpected state
     * @return the remaining time until token expiration, in nanoseconds
     */
    private long getRemainingTimeFromJwt(String token, long fallbackMs) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            // Parse the claims using standard JJWT
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return TimeUnit.MILLISECONDS.toNanos(fallbackMs);
            }

            long remainingMs = expiration.getTime() - System.currentTimeMillis();
            return TimeUnit.MILLISECONDS.toNanos(remainingMs);

        } catch (Exception e) {
            // If token is expired, malformed, or signature is invalid,
            // it doesn't need remaining time in the blacklist.
            return 0L;
        }
    }
}
