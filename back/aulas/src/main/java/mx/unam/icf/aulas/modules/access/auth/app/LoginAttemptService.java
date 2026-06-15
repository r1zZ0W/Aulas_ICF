package mx.unam.icf.aulas.modules.access.auth.app;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks consecutive failed login attempts per username and temporarily locks accounts.
 *
 * <p>After {@value #MAX_ATTEMPTS} consecutive failures the account is locked for
 * {@value #LOCK_MINUTES} minutes. A successful login resets the counter immediately.
 * The counter is stored in an in-memory Caffeine cache with a fixed write-time TTL —
 * the lock window starts at the first failure and expires {@value #LOCK_MINUTES} minutes later,
 * regardless of subsequent attempts.</p>
 *
 * <p>This bean is package-private; only {@link AuthUtils} within this package consumes it.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Component
class LoginAttemptService {

    static final int MAX_ATTEMPTS  = 3;
    static final int LOCK_MINUTES  = 10;

    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .expireAfterWrite(LOCK_MINUTES, TimeUnit.MINUTES)
            .build();

    /**
     * Returns {@code true} when the given username has reached or exceeded the maximum
     * allowed consecutive failed attempts and the lockout window has not yet expired.
     *
     * @param username login username to check
     */
    boolean isLocked(String username) {
        AtomicInteger count = attempts.getIfPresent(username);
        return count != null && count.get() >= MAX_ATTEMPTS;
    }

    /**
     * Increments the failure counter for the given username.
     * If this is the first failure, the lockout window timer starts now.
     *
     * @param username login username that failed authentication
     */
    void recordFailure(String username) {
        attempts.get(username, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Resets the failure counter for the given username.
     * Called immediately after a successful authentication.
     *
     * @param username login username that authenticated successfully
     */
    void reset(String username) {
        attempts.invalidate(username);
    }
}
