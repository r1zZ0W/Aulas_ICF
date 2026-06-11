package mx.unam.icf.aulas.kernel.infrastructure.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared utility for writing {@link ApiResponse} error payloads from servlet filters.
 *
 * <p>Filters run before Spring MVC's DispatcherServlet, so {@code @RestControllerAdvice}
 * cannot intercept their errors. This component bridges the gap: it serializes the same
 * {@link ApiResponse} envelope used by controllers, eliminating hardcoded JSON strings.</p>
 */
@Component
@RequiredArgsConstructor
public class FilterResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * Provides a safe HTTP response from the server that will be displayed into the frontend without using <code>ResponseEntity<?></code>
     * @param response          what the server will respond.
     * @param status            what HTTP status it will provide.
     * @param message           the message it will display in the JSON.
     * @throws IOException      the connection failure.
     */
    public void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}
