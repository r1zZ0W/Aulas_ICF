package mx.unam.icf.aulas.modules.access.auth.app;

/**
 * Contract for tracking consecutive failed login attempts per username and temporarily locking accounts.
 *
 * <p>Implementations must apply the same constants to guarantee consistent lockout behaviour
 * regardless of the backing store (in-memory or distributed).</p>
 *
 * @author Ithera
 * @version 1.0
 * @see LoginAttemptService  in-memory implementation
 */
public interface LoginAttemptStore {

    /** Maximum consecutive failures before the account is locked. */
    int MAX_ATTEMPTS = 3;

    /** Lock duration in minutes after reaching {@link #MAX_ATTEMPTS}. */
    int LOCK_MINUTES = 10;

    /**
     * Returns {@code true} when the given username has reached or exceeded the maximum
     * allowed consecutive failed attempts and the lockout window has not yet expired.
     */
    boolean isLocked(String username);

    /**
     * Increments the failure counter for the given username.
     * If this is the first failure, the lockout window timer starts now.
     */
    void recordFailure(String username);

    /**
     * Resets the failure counter for the given username.
     * Called immediately after a successful authentication.
     */
    void reset(String username);
}
