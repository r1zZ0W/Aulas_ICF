package mx.unam.icf.aulas.modules.reports.app.exceptions;

/**
 * Thrown when the reservations report PDF cannot be produced — template rendering,
 * font loading, or the HTML-to-PDF conversion failed.
 *
 * <p>This is an infrastructure failure, not a client error: it is mapped to HTTP 500
 * by the global exception handler. It deliberately does <em>not</em> extend
 * {@code DomainException} (which maps to 400) because the request itself was valid.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
