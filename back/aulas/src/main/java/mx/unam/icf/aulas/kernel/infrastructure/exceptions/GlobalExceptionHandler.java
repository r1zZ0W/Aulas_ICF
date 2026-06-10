package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.InvalidCredentialsException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.InvalidTokenException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.MissingTokenException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.TokenRevokedException;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
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
 * Centralized exception handler for all REST endpoints.
 *
 * <p>Converts application exceptions into consistent {@link ApiResponse} payloads with
 * the appropriate HTTP status code. All client-facing messages are in English and are
 * deliberately terse to avoid leaking implementation details or enabling account-enumeration
 * attacks.</p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Environment env;

    private boolean isDev() {
        return Arrays.asList(env.getActiveProfiles()).contains("dev");
    }

    // ── Domain / business ────────────────────────────────────────────────────

    /** Handles a missing entity lookup; returns 404 with the exception message as body. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Handles business-rule violations raised in the domain layer; returns 400 with the rule message. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── Security ─────────────────────────────────────────────────────────────

    /**
     * Handles explicit credential failures thrown by the auth service; returns 401.
     * The response message is intentionally generic to prevent account enumeration.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials."));
    }

    /** Handles requests that arrive without an Authorization header or token body; returns 401. */
    @ExceptionHandler(MissingTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingToken(MissingTokenException ex) {
        log.warn("Missing token: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication token is required."));
    }

    /** Handles tokens that fail signature verification, are expired, or carry the wrong type claim; returns 401. */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("The provided token is invalid or has expired."));
    }

    /** Handles tokens that were explicitly revoked (blacklisted in Redis after logout or password reset); returns 401. */
    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenRevoked(TokenRevokedException ex) {
        log.warn("Revoked token used: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("The provided token has been revoked."));
    }

    /** Safety net for {@link BadCredentialsException} thrown internally by Spring Security's AuthenticationManager; returns 401. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("BadCredentialsException (Spring Security): {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials."));
    }

    /** Handles role-based access control rejections from {@code @PreAuthorize}; returns 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action."));
    }

    // ── Input validation ─────────────────────────────────────────────────────

    /**
     * Handles Bean Validation failures ({@code @Valid} on controller parameters).
     * In {@code dev} profile the individual field errors are included; in production
     * a generic message is returned to avoid leaking field names.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = isDev()
                ? ex.getBindingResult().getFieldErrors().stream()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .collect(Collectors.joining("; "))
                : "The submitted data is not valid.";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    /** Handles unreadable or malformed JSON request bodies that cannot be deserialized; returns 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Request body is malformed or unreadable."));
    }

    /**
     * Handles path variable type mismatches (e.g., non-UUID string in a UUID path variable)
     * and explicit {@link IllegalArgumentException} from service/domain code; returns 400.
     * In {@code dev} profile the raw message is forwarded; in production a generic message is used.
     */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, IllegalArgumentException.class })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        String message = isDev() && ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Invalid parameters.";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /** Handles Spring Data access failures (query errors, connection issues, constraint violations); returns 500. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("The operation could not be completed. Please try again later."));
    }

    /** Handles low-level JPA/Hibernate persistence exceptions not caught by Spring's exception translation; returns 500. */
    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(PersistenceException ex) {
        log.error("JPA persistence error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("The operation could not be completed. Please try again later."));
    }

    // ── Infrastructure ────────────────────────────────────────────────────────

    /**
     * Handles failures from the email-sending infrastructure (SMTP errors, template issues); returns 500.
     * In {@code dev} profile the raw message is forwarded; in production a generic message is used.
     */
    @ExceptionHandler(MailSendingException.class)
    public ResponseEntity<ApiResponse<Void>> handleMailSending(MailSendingException ex) {
        log.error("Mail sending error", ex);
        String message = isDev() && ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "The email could not be sent. Please try again later.";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(message));
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    /**
     * Catch-all handler for any unhandled exception not matched by a more specific handler; returns 500.
     * In {@code dev} profile the raw exception message is included to aid debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        String message = isDev() && ex.getMessage() != null
                ? ex.getMessage()
                : "An internal error occurred. Please try again later.";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(message));
    }
}
