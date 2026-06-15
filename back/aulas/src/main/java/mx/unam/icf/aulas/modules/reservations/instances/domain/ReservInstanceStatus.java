package mx.unam.icf.aulas.modules.reservations.instances.domain;

/**
 * Lifecycle status of a {@link ReservInstance}.
 *
 * <p>Status transitions follow the approval workflow defined in the DFR:
 * a newly created instance starts as {@code PENDIENTE} and may be moved
 * to {@code APROBADA} or {@code RECHAZADA} by an administrator.
 * Both users and administrators may cancel an approved instance.</p>
 *
 * @author Ithera
 * @version 2.0
 */
public enum ReservInstanceStatus {

    /** Awaiting administrator review. Initial status on creation. */
    PENDIENTE,

    /** Approved by an administrator; classroom is reserved. */
    APROBADA,

    /** Rejected by an administrator; classroom is not reserved. */
    RECHAZADA,

    /** Cancelled by the teacher who owned the reservation. */
    CANCELADA_POR_MAESTRO,

    /** Cancelled by an administrator. */
    CANCELADA_POR_ADMIN
}
