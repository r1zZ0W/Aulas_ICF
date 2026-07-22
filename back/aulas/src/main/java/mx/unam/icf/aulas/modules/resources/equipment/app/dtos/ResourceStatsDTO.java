package mx.unam.icf.aulas.modules.resources.equipment.app.dtos;

/**
 * Aggregated statistics for the global resources admin dashboard.
 *
 * <p>Populated by a single grouped JPQL constructor-expression query in
 * {@code ResourceRepository.fetchStats()}, avoiding a full table scan.
 * Used by {@code GET /api/v1/resources/stats} (ADMIN only).</p>
 *
 * <p>{@code totalUnits} is {@code Long} (not {@code int}/{@code Integer}): a JPQL
 * {@code SUM} over an {@code Integer} column is projected by Hibernate as
 * {@code Long}, so a narrower type here would risk a {@code ClassCastException}
 * when the constructor-expression result is mapped.</p>
 *
 * @param totalTypes number of distinct resource types in the catalog
 * @param totalUnits sum of {@code quantity} across every resource type
 *
 * @author Ithera
 * @version 1.0
 */
public record ResourceStatsDTO(long totalTypes, Long totalUnits) {}
