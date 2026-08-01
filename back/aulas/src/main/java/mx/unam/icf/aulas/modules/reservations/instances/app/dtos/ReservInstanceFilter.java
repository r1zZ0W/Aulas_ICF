package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservationTimeframe;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Immutable value object carrying all optional server-side filter criteria
 * for the reservation-instance listing endpoints.
 *
 * <p>All fields are nullable; a {@code null} value means "no restriction on this dimension".
 * {@code userUuid} is set by the service layer when restricting results to a specific user
 * (e.g. for the Maestro's "Mis Reservas" view); it is never taken directly from a request
 * parameter.</p>
 *
 * @param search      case-insensitive substring matched against classroom name,
 *                    user firstName, user lastNames, and their null-safe concatenation;
 *                    ignored when {@code null} or blank
 * @param statuses    exact match against one or more {@link ReservInstanceStatus} values,
 *                    combined with OR (SQL {@code IN}); {@code null} or empty means no
 *                    restriction. Accepting a list (rather than a single value) is what lets
 *                    the "Cancelada" filter select both {@code CANCELLED_BY_USER} and
 *                    {@code CANCELLED_BY_ADMIN} in one query.
 * @param reassigned  equality filter on the {@code reassigned} flag ({@code true} = only
 *                    instances that were reassigned by an admin; {@code false} = only
 *                    instances that were never reassigned); ignored when {@code null}.
 *                    Combine with {@code statuses = [ACTIVE]} for a clean partition:
 *                    "Activa" ({@code ACTIVE + false}) vs "Reasignada" ({@code ACTIVE + true})
 * @param timeframe   restricts results to {@link ReservationTimeframe#UPCOMING} or
 *                    {@link ReservationTimeframe#PAST} relative to the reference date resolved
 *                    from the application {@code Clock}; ignored when {@code null}. Orthogonal
 *                    to {@code statuses} — e.g. {@code statuses=[ACTIVE], timeframe=PAST}
 *                    selects reservations that were never cancelled but whose date has passed
 *                    ("Finalizada").
 * @param classroomId equality filter on {@code classroom.uuid}; ignored when {@code null}
 * @param from        inclusive lower bound on {@code date} ({@code date >= from});
 *                    ignored when {@code null}
 * @param to          inclusive upper bound on {@code date} ({@code date <= to});
 *                    ignored when {@code null}
 * @param userUuid    equality filter on {@code group.user.uuid}; set internally by the
 *                    service to scope results to a specific user; ignored when {@code null}
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservInstanceFilter(
        String search,
        List<ReservInstanceStatus> statuses,
        Boolean reassigned,
        ReservationTimeframe timeframe,
        UUID classroomId,
        LocalDate from,
        LocalDate to,
        UUID userUuid
) {
    /**
     * Returns a copy of this filter with {@code userUuid} replaced by the given value.
     * Used by the service when building the "Mis Reservas" scoped query without
     * reconstructing the entire filter.
     *
     * <p>All other fields — including {@code reassigned} and {@code timeframe} — are
     * preserved verbatim so the maestro's scoped view inherits every active filter from
     * the request.</p>
     *
     * @param uuid the user UUID to scope results to
     * @return a new {@link ReservInstanceFilter} with all other fields preserved
     */
    public ReservInstanceFilter withUser(UUID uuid) {
        return new ReservInstanceFilter(search, statuses, reassigned, timeframe, classroomId, from, to, uuid);
    }
}
