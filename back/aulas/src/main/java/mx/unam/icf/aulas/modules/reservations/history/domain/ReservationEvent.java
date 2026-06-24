package mx.unam.icf.aulas.modules.reservations.history.domain;

/**
 * Enumeration of auditable events that can be recorded against a reservation.
 *
 * <p>Each constant represents a single, discrete state transition or action
 * that occurred on a {@link ReservationHistory} timeline. Only events that
 * correspond to <em>real</em> operations in the current system are listed here —
 * no speculative events (e.g. approval/rejection) are pre-declared, as no
 * approval workflow exists.</p>
 *
 * <p>Stored as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)} so
 * DB values are human-readable and unaffected by constant reordering.</p>
 *
 * @author Ithera
 * @version 1.0
 * @see ReservationHistory
 */
public enum ReservationEvent {

    /** A new reservation instance was successfully created. */
    CREATED,

    /** An existing reservation instance was modified (classroom, slots, attendees, etc.). */
    UPDATED,

    /** The owning teacher cancelled their reservation instance. */
    CANCELLED_BY_USER,

    /** An administrator cancelled the reservation instance. */
    CANCELLED_BY_ADMIN,

    /** An administrator reassigned the reservation to a different classroom or time block. */
    REASSIGNED
}
