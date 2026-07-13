package mx.unam.icf.aulas.modules.reservations.groups.domain;

/**
 * Lifecycle status of a {@link ReservationGroup}.
 *
 * <p>Note: the legacy {@code PENDING_ROSTER} state was removed — groups are created
 * in {@code ACTIVE} state and the system now relies on the presence of an on-disk
 * roster file to determine whether a first confirmation occurred.</p>
 *
 * @author Ithera
 * @version 1.1
 */
public enum ReservationGroupStatus {

    /** Group is active and confirmed — its student roster has been uploaded. */
    ACTIVE,

    /** Group has been cancelled; no new instances will be generated. */
    CANCELLED
}
