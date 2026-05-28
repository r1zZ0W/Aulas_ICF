package mx.unam.icf.aulas.modules.reservations.domain;

/**
 * Lifecycle status of an individual {@link ReservInstance}.
 *
 * @author Ithera
 * @version 1.0
 */
public enum ReservInstanceStatus {

    /** Instance is confirmed and occupies the reserved classroom slots. */
    CONFIRMED,

    /** Instance was cancelled by the owning user (MAESTRO). */
    CANCELLED_BY_USER,

    /** Instance was force-cancelled by an administrator (ADMIN). */
    CANCELLED_BY_ADMIN
}
