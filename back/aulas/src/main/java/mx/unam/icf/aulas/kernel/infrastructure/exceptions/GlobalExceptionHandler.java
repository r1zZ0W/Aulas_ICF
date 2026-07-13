package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.AccountLockedException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.InvalidCredentialsException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.InvalidTokenException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.MissingTokenException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.TokenRevokedException;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.reports.app.exceptions.ReportGenerationException;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ConflictDetailDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentCountMismatchDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentValidationErrorDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.DuplicateStudentException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.EmptyStudentListException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.StudentCountMismatchException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    /**
     * Handles slot conflicts detected during the atomic bulk booking ({@code POST /api/v1/reservations/booking}).
     * Returns HTTP 409 with a structured {@link ConflictDetailDTO} so the frontend can render a
     * human-readable message like "El martes 23 de junio a las 10:00 ya está ocupado" rather
     * than a generic error notice.
     */
    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ApiResponse<ConflictDetailDTO>> handleConflict(ReservationConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.<ConflictDetailDTO>builder()
                        .error(true)
                        .message(ex.getMessage())
                        .data(new ConflictDetailDTO(ex.getConflictDate(), ex.getConflictTimeSlotId()))
                        .build());
    }

    /**
     * Handles student-roster validation failures from the Excel upload flow
     * ({@code POST /api/v1/reservations/groups/{groupUuid}/students}): an empty workbook
     * (no data rows) or an intra-file duplicate student name. Returns HTTP 422 with a
     * structured {@link StudentValidationErrorDTO} — {@code row}/{@code value} populated
     * for a duplicate, both {@code null} for an empty roster — mirroring the 409 conflict
     * handler's pattern of a typed payload instead of a bare message.
     */
    @ExceptionHandler({ EmptyStudentListException.class, DuplicateStudentException.class })
    public ResponseEntity<ApiResponse<StudentValidationErrorDTO>> handleStudentListValidation(RuntimeException ex) {
        StudentValidationErrorDTO detail = (ex instanceof DuplicateStudentException dup)
                ? new StudentValidationErrorDTO(dup.getRow(), dup.getStudentFullName())
                : new StudentValidationErrorDTO(null, null);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.<StudentValidationErrorDTO>builder()
                        .error(true)
                        .message(ex.getMessage())
                        .data(detail)
                        .build());
    }

    /**
     * Handles a roster whose student count does not match the booking's declared
     * {@code attendeeCount} ({@code POST /api/v1/reservations/booking}). Returns HTTP 422
     * with a structured {@link StudentCountMismatchDTO} so the frontend can render a
     * message with both counts, mirroring {@link #handleStudentListValidation}'s pattern.
     */
    @ExceptionHandler(StudentCountMismatchException.class)
    public ResponseEntity<ApiResponse<StudentCountMismatchDTO>> handleStudentCountMismatch(
            StudentCountMismatchException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.<StudentCountMismatchDTO>builder()
                        .error(true)
                        .message(ex.getMessage())
                        .data(new StudentCountMismatchDTO(ex.getExpected(), ex.getActual()))
                        .build());
    }

    // ── Security ─────────────────────────────────────────────────────────────

    /** Returns 429 when an account is temporarily locked after too many failed login attempts. */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        log.warn("Locked account login attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("Account temporarily locked due to too many failed attempts. Try again in 10 minutes."));
    }

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

    /** Handles tokens that were explicitly revoked (blacklisted after logout or password reset); returns 401. */
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
     * Handles method-level validation failures raised by Spring Framework 6.1+'s built-in
     * method validation, which — depending on how the constraint is attached to a
     * {@code @RequestPart}/{@code @RequestParam} parameter — may fire instead of
     * {@link MethodArgumentNotValidException}. Defensive twin of {@link #handleValidation}
     * so multipart binding failures never fall through to the 500 catch-all.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = isDev()
                ? ex.getAllErrors().stream()
                        .map(err -> String.valueOf(err.getDefaultMessage()))
                        .collect(Collectors.joining("; "))
                : "The submitted data is not valid.";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    /**
     * Handles a multipart request that is missing a required part (e.g. the {@code file}
     * part of {@code POST /api/v1/reservations/booking}); returns 400 with the part name.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Required request part is missing: " + ex.getRequestPartName()));
    }

    /**
     * Handles uploads exceeding {@code spring.servlet.multipart.max-file-size}; returns 413.
     * Without this handler the exception fell through to the generic 500 catch-all, hiding
     * the actionable cause from the client.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.debug("Upload rejected, exceeds max size: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("The uploaded file exceeds the maximum allowed size."));
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

    /**
     * Handles failures from {@link mx.unam.icf.aulas.kernel.app.FileStorageService}
     * (disk write/read errors); returns 500. In {@code dev} profile the raw message is
     * forwarded; in production a generic message is used, matching {@link #handleMailSending}.
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorage(FileStorageException ex) {
        log.error("File storage error", ex);
        String message = isDev() && ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "The file could not be processed. Please try again later.";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(message));
    }

    /**
     * Handles failures while producing the reservations report PDF (template rendering,
     * embedded font loading, HTML-to-PDF conversion); returns 500. In {@code dev} profile
     * the raw message is forwarded; in production a generic message is used.
     */
    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleReportGeneration(ReportGenerationException ex) {
        log.error("Report generation error", ex);
        String message = isDev() && ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "The report could not be generated. Please try again later.";
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
