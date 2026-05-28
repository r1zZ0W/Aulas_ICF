package mx.unam.icf.aulas.modules.reservations.domain;

/**
 * Lifecycle status of a {@link ReservationGroup}.
 *
 * @author Ithera
 * @version 1.0
 */
public enum ReservationGroupStatus {

    /** Group is active and produces confirmed reservation instances. */
    ACTIVE,

    /** Group has been cancelled; no new instances will be generated. */
    CANCELLED
}
