package mx.unam.icf.aulas.modules.reports.app.projections;

/**
 * Spring Data interface projection for classroom slot-count aggregation queries.
 *
 * <p>Returned by {@code ReportStatisticsRepository.topClassroomsBySlots()}. Each instance
 * represents one classroom and the number of {@code ReservSlot} rows it holds within the
 * queried date range. The caller is responsible for converting {@code totalSlots} to hours
 * using the configured slot duration ({@code ReservationSlotProperties.slotDurationHours()}).</p>
 *
 * <p><b>Getter names must mirror the JPQL aliases</b> ({@code AS name}, {@code AS totalSlots})
 * in the repository query — Spring Data resolves interface projections by alias at runtime,
 * so a mismatch fails on execution, not compilation.</p>
 */
public interface ClassroomSlotsView {

    /**
     * Returns the classroom name as stored in {@code Classroom.name}.
     *
     * @return non-null classroom name
     */
    String getName();

    /**
     * Returns the total number of booked {@code ReservSlot} rows for this classroom in the period.
     * Multiply by {@code ReservationSlotProperties.slotDurationHours()} to get occupied hours.
     *
     * @return slot count; always {@code >= 0}
     */
    long getTotalSlots();
}
