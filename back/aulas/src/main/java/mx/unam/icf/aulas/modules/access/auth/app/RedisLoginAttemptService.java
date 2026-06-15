package mx.unam.icf.aulas.modules.access.auth.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed implementation of {@link LoginAttemptStore} for production environments.
 *
 * <p>Uses Redis atomic increment + TTL so that failed-attempt counters are shared across
 * all application instances, preventing an attacker from evading lockout by targeting
 * a different node in a horizontally scaled deployment.</p>
 *
 * <p>Redis key schema: {@code login:attempts:<username>}, TTL = {@value LoginAttemptStore#LOCK_MINUTES} minutes.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@Profile("prod")
@RequiredArgsConstructor
public class RedisLoginAttemptService implements LoginAttemptStore {

    private static final String PREFIX = "login:attempts:";

    private final StringRedisTemplate redis;

    @Override
    public boolean isLocked(String username) {
        String val = redis.opsForValue().get(PREFIX + username);
        if (val == null) return false;
        try {
            return Integer.parseInt(val) >= MAX_ATTEMPTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void recordFailure(String username) {
        String key = PREFIX + username;
        Long count = redis.opsForValue().increment(key);
        // Set (or refresh) TTL only on the first failure so the window starts at the first attempt
        if (count != null && count == 1) {
            redis.expire(key, LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public void reset(String username) {
        redis.delete(PREFIX + username);
    }
}
