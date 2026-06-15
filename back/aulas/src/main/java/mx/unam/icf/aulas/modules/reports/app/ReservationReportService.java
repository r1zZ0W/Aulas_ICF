package mx.unam.icf.aulas.modules.reports.app;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service that generates PDF reports of approved classroom reservations (DFR §5.1).
 *
 * <p>Supports two report periods ({@link ReportPeriod}) and an optional classroom filter.
 * The generated PDF contains a styled table with columns: Aula, Maestro, Fecha, Bloque, Estado, Motivo, Asistentes.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class ReservationReportService {

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "MX"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ReservInstanceRepository reservInstanceRepository;

    /**
     * Generates a PDF report of approved reservations for the given period.
     *
     * @param period        {@link ReportPeriod#MES_EN_CURSO} or {@link ReportPeriod#MES_ANTERIOR}
     * @param classroomUuid optional classroom filter; {@code null} means all classrooms
     * @return PDF bytes ready to be sent as {@code application/pdf}
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf(ReportPeriod period, UUID classroomUuid) {
        YearMonth month = period == ReportPeriod.MES_ANTERIOR
                ? YearMonth.now().minusMonths(1)
                : YearMonth.now();

        LocalDate from = month.atDay(1);
        // MES_EN_CURSO: cut off at today so future-dated approved instances are excluded
        LocalDate to = (period == ReportPeriod.MES_EN_CURSO)
                ? month.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : month.atEndOfMonth()
                : month.atEndOfMonth();

        String filterLabel = classroomUuid != null ? " — filtrado por aula" : "";
        String title = "Reporte de Reservas — " + month.format(MONTH_FMT) + filterLabel;

        List<ReservInstance> instances = (classroomUuid != null)
                ? reservInstanceRepository.findApprovedByClassroomAndDateRange(
                        classroomUuid, from, to, ReservInstanceStatus.APROBADA)
                : reservInstanceRepository.findApprovedByDateRange(
                        from, to, ReservInstanceStatus.APROBADA);

        return buildPdf(title, from, to, instances);
    }

    private byte[] buildPdf(String title, LocalDate from, LocalDate to, List<ReservInstance> data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (Document doc = new Document(PageSize.A4.rotate())) {
            PdfWriter.getInstance(doc, bos);
            doc.open();

            Font titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            Font subFont    = FontFactory.getFont(FontFactory.HELVETICA,      10, Color.DARK_GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, Color.WHITE);
            Font cellFont   = FontFactory.getFont(FontFactory.HELVETICA,       9, Color.BLACK);

            Paragraph heading = new Paragraph(title, titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            doc.add(heading);

            Paragraph range = new Paragraph(
                    "Período: " + from + " al " + to + "  |  Total: " + data.size() + " reservas",
                    subFont);
            range.setAlignment(Element.ALIGN_CENTER);
            range.setSpacingBefore(4);
            range.setSpacingAfter(12);
            doc.add(range);

            PdfPTable table = buildHeader(headerFont);

            Color rowAlt = new Color(235, 243, 252);
            for (int i = 0; i < data.size(); i++) {
                ReservInstance ri = data.get(i);
                Color bg = (i % 2 == 0) ? Color.WHITE : rowAlt;

                addCell(table, ri.getClassroom().getName(),              cellFont, bg);
                addCell(table, fullName(ri),                              cellFont, bg);
                addCell(table, ri.getDate().toString(),                   cellFont, bg);
                addCell(table, deriveBloque(ri),                          cellFont, bg);
                addCell(table, ri.getStatus().name(),                     cellFont, bg);
                addCell(table, ri.getMotivo() != null ? ri.getMotivo() : "", cellFont, bg);
                addCell(table, ri.getNumAsistentes() != null ? ri.getNumAsistentes().toString() : "", cellFont, bg);
            }

            doc.add(table);
        }

        return bos.toByteArray();
    }

    private @NonNull PdfPTable buildHeader(Font headerFont) {
        // Columns: Aula, Maestro, Fecha, Bloque, Estado, Motivo, Asistentes
        String[] headers = {"Aula", "Maestro", "Fecha", "Bloque", "Estado", "Motivo", "Asistentes"};
        float[]  widths  = { 2f,     2.5f,      1.5f,    2f,       2f,       3f,       1.2f};

        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        Color headerBg = new Color(33, 96, 150);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        return table;
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4);
        table.addCell(cell);
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
    private String deriveBloque(ReservInstance ri) {
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
