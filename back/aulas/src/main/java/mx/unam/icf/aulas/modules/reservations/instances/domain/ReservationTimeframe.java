package mx.unam.icf.aulas.modules.reservations.instances.domain;

/**
 * Temporal classification of a {@link ReservInstance} relative to the current date.
 *
 * <p>This is <strong>not</strong> a lifecycle status: it is derived on every read from
 * {@code date} and never stored. A reservation moves from {@link #UPCOMING} to
 * {@link #PAST} on its own, without any job rewriting rows — see
 * {@link ReservationTimeframeRule} for the exact boundary.</p>
 *
 * <p>It is orthogonal to {@link ReservInstanceStatus}: a cancelled reservation still has a
 * timeframe, but consumers give the cancellation precedence when labelling it.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public enum ReservationTimeframe {

    /** The reservation's date is today or later — it still belongs to the agenda. */
    UPCOMING,

    /** The reservation's date is strictly before today — it belongs to the history. */
    PAST
}
