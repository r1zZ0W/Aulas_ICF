package mx.unam.icf.aulas.modules.reservations.slots.app;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties that define the core temporal unit of the reservations domain:
 * the duration of a single booked time slot ({@code ReservSlot}).
 *
 * <p>Encapsulating this value in the reservations bounded context (rather than reading an
 * {@code @Value} directly in reporting or other cross-cutting services) makes the slot duration
 * a <em>reusable domain rule</em>. Any future service that needs to convert slot counts to
 * hours — conflict-penalty calculations, capacity utilisation reporting, billing integrations —
 * can inject this component instead of duplicating the property binding.</p>
 *
 * <p>Properties are read from the {@code app.reservations} prefix in
 * {@code application.properties}. The default ({@code 30} minutes) reflects the current
 * ICF academic schedule.</p>
 *
 * @see mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.reservations")
public class ReservationSlotProperties {

    /**
     * Duration of one reservation time slot in minutes.
     *
     * <p>Default is {@code 30}, reflecting the standard ICF block length.
     * Override via {@code app.reservations.slot-duration-minutes} in
     * {@code application.properties} or the corresponding environment variable if the
     * institution adopts a different schedule.</p>
     */
    private int slotDurationMinutes = 30;

    /**
     * Converts the configured slot duration to fractional hours.
     *
     * <p>Used to translate a raw slot count (as returned by an aggregation query) into
     * meaningful "occupied hours" for KPI cards and bar charts. For the default 30-minute
     * slot, this returns {@code 0.5}.</p>
     *
     * @return slot duration expressed as a fraction of an hour
     *         (e.g., {@code 0.5} for 30 min, {@code 0.75} for 45 min)
     */
    public double slotDurationHours() {
        return slotDurationMinutes / 60.0;
    }
}
