package mx.unam.icf.aulas.modules.reservations.groups.domain;

/**
 * Lifecycle status of a {@link ReservationGroup}.
 *
 * @author Ithera
 * @version 1.0
 */
public enum ReservationGroupStatus {

    /** Group is active and can produce confirmed reservation instances. */
    ACTIVE,

    /** Group has been cancelled; no new instances will be generated. */
    CANCELLED
}
