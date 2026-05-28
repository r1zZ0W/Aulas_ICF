package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for REST endpoints.
 * It converts application exceptions into consistent {@link ApiResponse} payloads
 * with the corresponding HTTP status code.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Environment env;

    /**
     * Indicates whether the current runtime profile includes {@code dev}.
     *
     * @return {@code true} when the dev profile is active; otherwise {@code false}
     */
    private boolean isDev() {
        return Arrays.asList(env.getActiveProfiles()).contains("dev");
    }

    /**
     * Handles missing-resource errors.
     *
     * @param ex the thrown resource-not-found exception
     * @return a 404 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles domain validation and business-rule errors.
     *
     * @param ex the thrown domain exception
     * @return a 400 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles authorization failures.
     * @param e the thrown access-denied exception
     * @return a 403 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.debug("Access denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("No tienes permiso para realizar esta acción"));
    }

    /**
     *
     * In the dev profile, detailed field errors are returned.
     *
     * @param e the validation exception
     * @return a 400 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String mensaje;
        if (isDev()) {
            mensaje = e.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            mensaje = "Los datos enviados no son válidos.";
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(mensaje));
    }

    /**
     * Handles malformed JSON payloads or unreadable request bodies.
     *
     * @param e the unreadable-message exception
     * @return a 400 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.debug("JSON inválido o cuerpo ilegible: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Solicitud con formato incorrecto."));
    }

    /**
     * Handles invalid argument or parameter type mismatch scenarios.
     *
     * @param e the bad-request related exception
     * @return a 400 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, IllegalArgumentException.class })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        String mensaje = isDev() && e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : "Parámetros inválidos.";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(mensaje));
    }

    /**
     * Handles data-access layer exceptions.
     *
     * @param e the data-access exception
     * @return a 500 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e) {
        log.error("Error de persistencia o SQL (detalle solo en servidor)", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("No se pudo completar la operación. Intenta más tarde."));
    }

    /**
     * Handles JPA persistence exceptions.
     *
     * @param e the persistence exception
     * @return a 500 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(PersistenceException e) {
        log.error("Error de persistencia JPA (detalle solo en servidor)", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("No se pudo completar la operación. Intenta más tarde."));
    }

    /**
     * Handles mail sending failures.
     *
     * @param e the mail sending exception
     * @return a 500 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(MailSendingException.class)
    public ResponseEntity<ApiResponse<Void>> handleMailSending(MailSendingException e) {
        log.error("Error trying to send the email.", e);
        String msg = isDev() && e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : "We can not retrieve this email. Please try later.";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(msg));
    }

    /**
     * Fallback handler for uncaught exceptions.
     *
     * @param e the unexpected exception
     * @return a 500 response wrapped in {@link ApiResponse}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("Error inesperado", e);
        String mensaje = isDev() && e.getMessage() != null
                ? e.getMessage()
                : "Ocurrió un error interno. Intenta más tarde.";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(mensaje));
    }
}
