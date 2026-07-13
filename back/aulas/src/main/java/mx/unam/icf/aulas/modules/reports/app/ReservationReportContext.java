package mx.unam.icf.aulas.modules.reports.app;

import java.time.LocalDate;
import java.util.List;

/**
 * Display-ready data for the reservations PDF report, decoupled from JPA entities.
 *
 * <p>Every {@link Row} field is a pre-formatted string: the service resolves entities,
 * derives time blocks, and formats names/dates so the rendering layer (Thymeleaf template
 * + PDF engine) stays purely presentational and never touches the domain model.</p>
 *
 * @param title report heading, e.g. {@code "Reporte de Reservas — julio 2026"}
 * @param from  first day covered by the report (inclusive)
 * @param to    last day covered by the report (inclusive)
 * @param total number of reservations included
 * @param rows  one entry per reservation, in presentation order
 *
 * @author Ithera
 * @version 1.0
 */
public record ReservationReportContext(
        String title,
        LocalDate from,
        LocalDate to,
        int total,
        List<Row> rows
) {

    /**
     * One table row of the report.
     *
     * @param classroom classroom name ("Aula")
     * @param teacher   owning teacher's full name ("Maestro")
     * @param date      reservation date as ISO string ("Fecha")
     * @param timeBlock human-readable time block, e.g. {@code "07:00 – 08:30"} ("Bloque")
     * @param status    reservation status name ("Estado")
     * @param attendees expected attendee count, empty when unknown ("Asistentes")
     */
    public record Row(
            String classroom,
            String teacher,
            String date,
            String timeBlock,
            String status,
            String attendees
    ) {}
}
