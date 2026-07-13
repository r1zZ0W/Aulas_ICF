package mx.unam.icf.aulas.modules.reports.app;

/**
 * Generates a PDF representation of the reservations report.
 *
 * <p>Port of the reports module, mirroring
 * {@link mx.unam.icf.aulas.modules.reservations.students.app.StudentListPdfGenerator}:
 * the application service builds a {@link ReservationReportContext} and stays agnostic
 * of the rendering technology (the infrastructure adapter renders HTML via Thymeleaf
 * and converts it with openhtmltopdf).</p>
 *
 * @author Ithera
 * @version 1.0
 */
public interface ReservationReportPdfGenerator {

    /**
     * Builds the report PDF entirely in memory.
     *
     * @param context display-ready report data (title, period, rows)
     * @return PDF bytes ready to be sent as {@code application/pdf}
     * @throws mx.unam.icf.aulas.modules.reports.app.exceptions.ReportGenerationException
     *         when template rendering or PDF conversion fails
     */
    byte[] generate(ReservationReportContext context);
}
