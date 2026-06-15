package mx.unam.icf.aulas.modules.access.auth.app;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory (Caffeine) implementation of {@link LoginAttemptStore} for non-production environments.
 *
 * <p>Tracks consecutive failed login attempts per username in a local heap-based cache.
 * After {@link #MAX_ATTEMPTS} consecutive failures the account is locked for {@link #LOCK_MINUTES} minutes.
 * A successful login resets the counter immediately.
 * The counter TTL starts at the first failure and expires {@value #LOCK_MINUTES} minutes later,
 * regardless of subsequent attempts (write-expiry semantics).</p>
 *
 * <p>This bean is active in all profiles except {@code prod}. For production / multi-instance
 * deployments, use {@link RedisLoginAttemptService} instead.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@Profile("!prod")
public class LoginAttemptService implements LoginAttemptStore {

    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .expireAfterWrite(LOCK_MINUTES, TimeUnit.MINUTES)
            .build();

    @Override
    public boolean isLocked(String username) {
        AtomicInteger count = attempts.getIfPresent(username);
        return count != null && count.get() >= MAX_ATTEMPTS;
    }

    @Override
    public void recordFailure(String username) {
        attempts.get(username, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public void reset(String username) {
        attempts.invalidate(username);
    }
}
