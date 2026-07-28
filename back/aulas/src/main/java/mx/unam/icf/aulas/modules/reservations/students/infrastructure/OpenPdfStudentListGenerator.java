package mx.unam.icf.aulas.modules.reservations.students.infrastructure;

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
import mx.unam.icf.aulas.modules.reservations.students.app.StudentListPdfGenerator;
import mx.unam.icf.aulas.modules.reservations.students.app.StudentRosterContext;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException;
import mx.unam.icf.aulas.modules.reservations.students.domain.Student;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * OpenPDF implementation of {@link StudentListPdfGenerator}.
 *
 * <p>Follows the institutional report style (blue header row, alternating row
 * backgrounds): an A4 document built entirely in memory with {@link PdfPTable}.
 * Columns mirror the source spreadsheet: Nombre(s) | Apellido(s) | Correo electrónico.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Component
public class OpenPdfStudentListGenerator implements StudentListPdfGenerator {

    @Override
    public byte[] generate(List<Student> students, StudentRosterContext context) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (Document doc = new Document(PageSize.A4)) {
            PdfWriter.getInstance(doc, bos);
            doc.open();

            Font titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            Font subFont    = FontFactory.getFont(FontFactory.HELVETICA,      10, Color.DARK_GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, Color.WHITE);
            Font cellFont   = FontFactory.getFont(FontFactory.HELVETICA,       9, Color.BLACK);

            Paragraph heading = new Paragraph("Lista de Alumnos — " + context.groupLabel(), titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            doc.add(heading);

            Paragraph subtitle = new Paragraph(
                    "Maestro: " + context.teacherFullName() + "  |  Total: " + context.totalStudents() + " alumnos",
                    subFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingBefore(4);
            subtitle.setSpacingAfter(12);
            doc.add(subtitle);

            PdfPTable table = buildHeader(headerFont);

            Color rowAlt = new Color(235, 243, 252);
            for (int i = 0; i < students.size(); i++) {
                Student s = students.get(i);
                Color bg = (i % 2 == 0) ? Color.WHITE : rowAlt;

                addCell(table, s.firstName(), cellFont, bg);
                addCell(table, s.lastName(),  cellFont, bg);
                addCell(table, s.email(),     cellFont, bg);
            }

            doc.add(table);
        } catch (Exception e) {
            // Should not happen for well-formed in-memory data, but a generator failure
            // is still a "the source data could not be turned into the expected output"
            // problem — reuse InvalidExcelFileException rather than leaking a 500.
            throw new InvalidExcelFileException(ErrorCode.ROSTER_PDF_FAILED, "Failed to generate the student roster PDF.", e);
        }

        return bos.toByteArray();
    }

    private @NonNull PdfPTable buildHeader(Font headerFont) {
        String[] headers = {"Nombre(s)", "Apellido(s)", "Correo electrónico"};
        float[]  widths  = { 3f,          3f,             4f};

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
}
