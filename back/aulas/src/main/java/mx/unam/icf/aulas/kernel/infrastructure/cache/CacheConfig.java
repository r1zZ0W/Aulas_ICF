package mx.unam.icf.aulas.kernel.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

// Per-entry TTL via Caffeine's Expiry so each blacklisted token expires exactly when
// its own JWT exp claim does, mirroring the behaviour of Redis' key-level TTL.
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Profile("!prod")
    public CacheManager cacheManager(@Value("${jwt.expiration:3600000}") long jwtExpirationMs) {
        CaffeineCacheManager manager = new CaffeineCacheManager("token-blacklist");
        manager.setCaffeine(
            Caffeine.newBuilder().expireAfter(new Expiry<Object, Object>() {
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
     * TODO: replace stub with real JWT parsing.
     * Extract the {@code exp} claim, subtract the current epoch-millisecond time,
     * and return the difference converted to nanoseconds. Example with JJWT:
     *
     *   Date exp = Jwts.parserBuilder()
     *       .setSigningKey(signingKey)
     *       .build()
     *       .parseClaimsJws(token)
     *       .getBody()
     *       .getExpiration();
     *   long remainingMs = exp.getTime() - System.currentTimeMillis();
     *   return TimeUnit.MILLISECONDS.toNanos(remainingMs);
     */
    private long getRemainingTimeFromJwt(String token, long fallbackMs) {
        return TimeUnit.MILLISECONDS.toNanos(fallbackMs);
    }
}
