package mx.unam.icf.aulas.modules.reports.app.dtos;

import java.util.List;

/**
 * Immutable data-transfer object that carries all aggregated statistics for the
 * "Reportes y Estadísticas" dashboard ({@code GET /api/v1/reports/statistics}).
 *
 * <p>The shape of this record mirrors exactly the {@code ReservationStatisticsSchema} Zod schema
 * defined in {@code front/icf-aulas/src/schemas/report.js}. The {@code label} field inside
 * {@link TendenciaItem} accepts any {@code string} in the Zod schema, so semester-specific
 * labels ({@code "Ago"}, {@code "Ene"}, etc.) are rendered correctly by the chart without
 * requiring frontend changes.</p>
 *
 * <p>All collection fields are non-null and may be empty when the period has no data.
 * {@link #totalReservasDeltaPct()}, {@link #aulaMasOcupada()}, and {@link #mayorUsuario()} are
 * boxed / nullable to signal the absence of comparable data without using sentinel values.</p>
 *
 * <p>{@link #tendencia()} is <em>never</em> empty — the service always populates the full
 * scaffold of bucket labels (with {@code reservas = 0} for empty buckets) so that chart
 * components can render their axes even when the period has no reservations.</p>
 */
public record ReservationStatisticsDTO(

        /**
         * Total number of active {@code ReservInstance} records within the analysis period.
         * Always {@code >= 0}.
         */
        long totalReservas,

        /**
         * Percentage change in total reservations versus the immediately preceding comparable
         * period. The previous period is always truncated proportionally: if the current period
         * is still open (e.g., 1–15 Jun), the previous period covers the same elapsed days
         * (1–15 May); if the current period has closed, the previous period is taken complete.
         *
         * <p>{@code null} when the previous period had zero reservations (prevents
         * division-by-zero) or when no previous period exists in the database.</p>
         */
        Double totalReservasDeltaPct,

        /**
         * The classroom with the most occupied hours in the period.
         * {@code null} when no active reservations exist.
         */
        AulaOcupacion aulaMasOcupada,

        /**
         * The user with the highest reservation count in the period.
         * {@code null} when no active reservations exist.
         */
        UsuarioReservas mayorUsuario,

        /**
         * Fraction of <em>reservations</em> (distinct {@code ReservationGroup}s with at least one
         * active instance in the analysis period) that are <em>recurring</em>, expressed as a
         * percentage in the range {@code [0, 100]}.
         *
         * <p>Counted by reservation (group), not by session-day: a weekly class with 12 sessions
         * in the period contributes {@code 1} to the recurring count, not 12. A group is
         * recurring when it has more than one active instance <em>within {@code [from, to]}</em>;
         * eventual when it has exactly one. {@code tasaRecurrenciaPct = recurrentes / (recurrentes
         * + eventuales) * 100}, where the denominator is the number of distinct groups with
         * instances in the period (not {@link #totalReservas()}, which counts session-days).</p>
         */
        double tasaRecurrenciaPct,

        /**
         * Top-5 classrooms ordered by total occupied hours descending.
         * Empty (never {@code null}) when no active reservations exist.
         */
        List<AulaOcupacion> aulasMasOcupadas,

        /**
         * Top-5 users ordered by reservation count descending.
         * Empty (never {@code null}) when no active reservations exist.
         */
        List<UsuarioReservas> usuariosMasReservas,

        /**
         * Recurrent vs. eventual <em>reservation</em> (group) split for the donut chart.
         * {@code recurrentes + eventuales} equals the number of distinct {@code ReservationGroup}s
         * with instances in the period — <b>not</b> {@link #totalReservas()}, which counts
         * session-days. Both counts are {@code 0} when the period has no reservations.
         */
        Recurrencia recurrencia,

        /**
         * Time-series buckets for the trend area chart. Never empty.
         * <ul>
         *   <li>{@code MENSUAL}: one entry per calendar day of the selected month
         *       ({@code "01"…"28/29/30/31"}); {@code reservas = 0} for days without data.</li>
         *   <li>{@code SEMESTRAL}: one entry per calendar month of the selected semester;
         *       labels are in es-MX abbreviated form ({@code "Ene"…"Dic"}) and cover all
         *       semester months including future ones (with {@code reservas = 0}).</li>
         * </ul>
         */
        List<TendenciaItem> tendencia

) {

    // ── Nested records ────────────────────────────────────────────────────────

    /**
     * Classroom name paired with its total occupied hours for a given period.
     *
     * @param nombre classroom name as stored in {@code Classroom.name}
     * @param horas  total hours computed as {@code slotCount × slotDurationHours}; always {@code >= 0}
     */
    public record AulaOcupacion(String nombre, double horas) {}

    /**
     * User full name paired with their reservation count for a given period.
     *
     * @param nombre   user's display name ({@code TRIM(CONCAT(firstName, ' ', COALESCE(lastNames, '')))})
     * @param reservas number of active {@code ReservInstance} records owned by this user; always {@code >= 0}
     */
    public record UsuarioReservas(String nombre, long reservas) {}

    /**
     * Recurrent vs. eventual <em>reservation</em> (group) split for the donut chart.
     *
     * <p>A reservation ({@code ReservationGroup}) is <em>recurrente</em> when it has more than
     * one active instance within the analysis period; <em>eventual</em> when it has exactly one.
     * Counted by reservation, not by session-day, so this invariant holds:
     * {@code recurrentes + eventuales == } number of distinct groups with instances in the
     * period (not {@code totalReservas}).</p>
     *
     * @param recurrentes reservations (groups) with {@code > 1} active instances in the period
     * @param eventuales  reservations (groups) with exactly {@code 1} active instance in the period
     */
    public record Recurrencia(long recurrentes, long eventuales) {}

    /**
     * Single time-series data point for the trend area chart.
     *
     * @param label    bucket identifier:
     *                 zero-padded day-of-month ({@code "01"…"31"}) for {@code MENSUAL}, or
     *                 Spanish abbreviated month ({@code "Ene"…"Dic"}) for {@code SEMESTRAL}
     * @param reservas number of active instances in this time bucket; {@code 0} for empty buckets
     */
    public record TendenciaItem(String label, long reservas) {}
}
