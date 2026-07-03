package mx.unam.icf.aulas.modules.reservations.groups.domain;

/**
 * Lifecycle status of a {@link ReservationGroup}.
 *
 * @author Ithera
 * @version 1.0
 */
public enum ReservationGroupStatus {

    /**
     * Group has been created and its
     * {@link mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance} occurrences
     * already occupy their classrooms, but the mandatory student roster has not yet been
     * uploaded and confirmed. Admins are not notified while a group sits in this state.
     *
     * <p>Groups left in this state past a grace period are reaped by
     * {@code StudentRosterCleanupJob} to avoid classrooms being held indefinitely
     * by a reservation that never received its roster.</p>
     */
    PENDING_ROSTER,

    /** Group is active and confirmed — its student roster has been uploaded. */
    ACTIVE,

    /** Group has been cancelled; no new instances will be generated. */
    CANCELLED
}
