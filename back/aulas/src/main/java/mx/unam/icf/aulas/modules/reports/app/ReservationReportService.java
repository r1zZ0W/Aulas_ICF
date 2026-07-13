package mx.unam.icf.aulas.modules.reports.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service that generates PDF reports of active classroom reservations.
 *
 * <p>Supports two report periods ({@link ReportPeriod}) and an optional classroom filter.
 * The service resolves the period window, queries the matching instances, and maps them
 * into a display-ready {@link ReservationReportContext}; the actual PDF rendering is
 * delegated to the {@link ReservationReportPdfGenerator} port (Thymeleaf + openhtmltopdf
 * in the current adapter), keeping this layer free of any rendering-library dependency.</p>
 *
 * @author Ithera
 * @version 4.0
 */
@Service
@RequiredArgsConstructor
public class ReservationReportService {

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "MX"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ReservInstanceRepository reservInstanceRepository;
    private final ReservationReportPdfGenerator pdfGenerator;

    /**
     * Generates a PDF report of active reservations for the given period.
     *
     * @param period        the month to report on (current or previous)
     * @param classroomUuid optional classroom filter; {@code null} means all classrooms
     * @return PDF bytes ready to be sent as {@code application/pdf}
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf(ReportPeriod period, UUID classroomUuid) {
        YearMonth month = period == ReportPeriod.PREVIOUS_MONTH
                ? YearMonth.now().minusMonths(1)
                : YearMonth.now();

        LocalDate from = month.atDay(1);
        // CURRENT_MONTH: cut off at today so future-dated active instances are excluded
        LocalDate to = (period == ReportPeriod.CURRENT_MONTH)
                ? month.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : month.atEndOfMonth()
                : month.atEndOfMonth();

        String filterLabel = classroomUuid != null ? " — filtrado por aula" : "";
        String title = "Reporte de Reservas — " + month.format(MONTH_FMT) + filterLabel;

        List<ReservInstance> instances = (classroomUuid != null)
                ? reservInstanceRepository.findActiveByClassroomAndDateRange(
                        classroomUuid, from, to, ReservInstanceStatus.ACTIVE)
                : reservInstanceRepository.findActiveByDateRange(
                        from, to, ReservInstanceStatus.ACTIVE);

        List<ReservationReportContext.Row> rows = instances.stream()
                .map(this::toRow)
                .toList();

        return pdfGenerator.generate(
                new ReservationReportContext(title, from, to, rows.size(), rows));
    }

    /** Maps one reservation instance to a fully formatted report row. */
    private ReservationReportContext.Row toRow(ReservInstance ri) {
        return new ReservationReportContext.Row(
                ri.getClassroom().getName(),
                fullName(ri),
                ri.getDate().toString(),
                deriveTimeBlock(ri),
                ri.getStatus().name(),
                ri.getAttendeeCount() != null ? ri.getAttendeeCount().toString() : "");
    }

    private String fullName(ReservInstance ri) {
        var user = ri.getGroup().getUser();
        return user.getFirstName() + " " + user.getLastNames();
    }

    /**
     * Derives a human-readable time block from the reservation's slots,
     * e.g. {@code "07:00 – 08:30"}.
     * Returns {@code "—"} when no slots are associated.
     */
    private String deriveTimeBlock(ReservInstance ri) {
        List<ReservSlot> slots = ri.getSlots();
        if (slots == null || slots.isEmpty()) return "—";

        var minSlot = slots.stream()
                .min(Comparator.comparing(s -> s.getTimeSlot().getStartTime()))
                .orElseThrow();
        var maxSlot = slots.stream()
                .max(Comparator.comparing(s -> s.getTimeSlot().getEndTime()))
                .orElseThrow();

        return minSlot.getTimeSlot().getStartTime().format(TIME_FMT)
                + " – "
                + maxSlot.getTimeSlot().getEndTime().format(TIME_FMT);
    }
}
