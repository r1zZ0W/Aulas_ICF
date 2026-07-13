package mx.unam.icf.aulas.modules.reports.infrastructure;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.reports.app.ReservationReportContext;
import mx.unam.icf.aulas.modules.reports.app.ReservationReportPdfGenerator;
import mx.unam.icf.aulas.modules.reports.app.exceptions.ReportGenerationException;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * openhtmltopdf implementation of {@link ReservationReportPdfGenerator}.
 *
 * <p>Renders {@code templates/reports/reservation-report.html} with the same Spring-managed
 * Thymeleaf {@link TemplateEngine} the email notifications use, then converts the resulting
 * XHTML into PDF bytes with {@link PdfRendererBuilder}. The template must therefore be
 * <em>well-formed XHTML</em> (self-closed tags) with all CSS inlined in a {@code <style>}
 * block — openhtmltopdf resolves external URIs against a base URL that does not exist
 * inside a Spring Boot fat JAR.</p>
 *
 * <h3>Fonts</h3>
 * <p>DejaVu Sans (regular + bold) is embedded from {@code classpath:fonts/} so accented
 * Spanish text and any glyph outside CP1252 render correctly; PDFBox's built-in base-14
 * fonts silently drop out-of-range characters. Resources are resolved through Spring's
 * {@link ResourceLoader} — never {@code File} paths — so resolution behaves identically
 * in the IDE, in tests, and inside the packaged fat JAR, and a missing font surfaces as
 * an explicit {@link ReportGenerationException} instead of a cryptic NPE.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class OpenHtmlToPdfReservationReportGenerator implements ReservationReportPdfGenerator {

    /** Must match the {@code font-family} used by the template's CSS exactly. */
    private static final String FONT_FAMILY = "DejaVu Sans";

    private static final String TEMPLATE = "reports/reservation-report";

    private final TemplateEngine templateEngine;
    private final ResourceLoader resourceLoader;

    @Override
    public byte[] generate(ReservationReportContext context) {
        Context ctx = new Context();
        ctx.setVariable("title", context.title());
        ctx.setVariable("from", context.from());
        ctx.setVariable("to", context.to());
        ctx.setVariable("total", context.total());
        ctx.setVariable("rows", context.rows());

        String html = templateEngine.process(TEMPLATE, ctx);

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> fontStream("classpath:fonts/DejaVuSans.ttf"),
                    FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> fontStream("classpath:fonts/DejaVuSans-Bold.ttf"),
                    FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(bos);
            builder.run();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new ReportGenerationException("Could not convert the reservations report to PDF.", e);
        }
    }

    /**
     * Opens an embedded font from the classpath, failing loudly: openhtmltopdf would turn
     * a {@code null} supplier result into an unrelated NPE deep inside the renderer.
     */
    private InputStream fontStream(String location) {
        try {
            return resourceLoader.getResource(location).getInputStream();
        } catch (IOException e) {
            throw new ReportGenerationException("Could not load embedded report font: " + location, e);
        }
    }
}
