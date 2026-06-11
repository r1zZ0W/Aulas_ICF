package mx.unam.icf.aulas.kernel.infrastructure.jwt;

/**
 * Service interface for managing a JSON Web Token (JWT) blacklist.
 * <p>
 * This contract provides mechanisms to revoke tokens prematurely (e.g., during logout)
 * before their natural expiration time has passed.
 * </p>
 */
public interface TokenBlacklist {

    /**
     * Adds a token identifier to the blacklist with a specific Time-To-Live (TTL).
     *
     * @param jti        the unique JWT ID claim (jti) or token identifier to invalidate
     * @param ttlSeconds the duration in seconds for which the token must remain blacklisted
     */
    void blacklist(String jti, long ttlSeconds);

    /**
     * Checks whether a specific token identifier is currently blacklisted.
     *
     * @param jti the unique JWT ID claim (jti) or token identifier to verify
     * @return {@code true} if the token is blacklisted and should be rejected, {@code false} otherwise
     */
    boolean isBlacklisted(String jti);
}
