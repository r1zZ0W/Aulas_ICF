package mx.unam.icf.aulas.modules.reservations.instances.domain;

/**
 * Lifecycle status of a {@link ReservInstance}.
 *
 * <p>A reservation is active ({@code ACTIVA}) from the moment it is created and occupies
 * the classroom immediately — there is no pending or approval step. The only allowed
 * transition is from {@code ACTIVA} to one of the cancelled states, which physically
 * releases the reserved time slots so the classroom can be rebooked.</p>
 *
 * @author Ithera
 * @version 3.0
 */
public enum ReservInstanceStatus {

    /** Active reservation; classroom is occupied for the specified date and time slots. */
    ACTIVE,

    /** Cancelled by the teacher who owned the reservation. Slots have been freed. */
    CANCELLED_BY_USER,

    /** Cancelled by an administrator. Slots have been freed. */
    CANCELLED_BY_ADMIN
}
