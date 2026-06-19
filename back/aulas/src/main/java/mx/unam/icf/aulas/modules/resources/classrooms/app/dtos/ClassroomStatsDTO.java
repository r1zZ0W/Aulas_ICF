package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

/**
 * Aggregated statistics for the classrooms admin dashboard.
 *
 * <p>Populated by a single grouped JPQL query in {@code ClassroomRepository.fetchStats()},
 * avoiding a full table scan. Used by {@code GET /api/v1/classrooms/stats} (ADMIN only).</p>
 *
 * @param total        total number of classrooms in the system
 * @param available    classrooms whose {@code isActive = true}
 * @param notAvailable classrooms whose {@code isActive = false} or {@code null}
 *
 * @author Ithera
 * @version 1.0
 */
public record ClassroomStatsDTO(long total, long available, long notAvailable) {}
