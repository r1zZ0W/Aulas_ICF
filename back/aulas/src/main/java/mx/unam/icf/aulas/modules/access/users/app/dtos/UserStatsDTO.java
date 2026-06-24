package mx.unam.icf.aulas.modules.access.users.app.dtos;

/**
 * Aggregated statistics for the users admin dashboard.
 *
 * <p>Populated by a single grouped JPQL query in {@code UserRepository.fetchStats()},
 * avoiding a full table scan. Used by {@code GET /api/v1/users/stats} (ADMIN only).</p>
 *
 * @param total    total number of users in the system
 * @param admins   users assigned the {@code ADMIN} role
 *
 * @author Ithera
 * @version 1.0
 */
public record UserStatsDTO(long total, long admins) {}
