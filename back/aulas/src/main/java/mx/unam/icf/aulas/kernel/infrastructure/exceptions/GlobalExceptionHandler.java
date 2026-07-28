package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.AccountLockedException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.InvalidCredentialsException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.InvalidTokenException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.MissingTokenException;
import mx.unam.icf.aulas.modules.access.auth.app.exceptions.TokenRevokedException;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ValidationErrorDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ConflictDetailDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentCountMismatchDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentValidationErrorDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.DuplicateStudentException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.EmptyStudentListException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.StudentCountMismatchException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized exception handler for all REST endpoints.
 *
 * <p>Converts application exceptions into consistent {@link ApiResponse} payloads with
 * the appropriate HTTP status code and a stable {@link ErrorCode}. The {@code message}
 * field is English prose kept for logs/debugging — the client never renders it directly;
 * it resolves {@code code} to Spanish text via its own catalog.</p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Environment env;

    private boolean isDev() {
        return env.acceptsProfiles(Profiles.of("dev"));
    }

    // ── Domain / business ────────────────────────────────────────────────────

    /** Handles a missing entity lookup; returns 404 with the exception's own code. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /** Handles business-rule violations raised in the domain layer; returns 400 with the exception's own code. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
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
                .body(ApiResponse.error(
                        ErrorCode.RESERVATION_SLOT_CONFLICT,
                        ex.getMessage(),
                        new ConflictDetailDTO(ex.getConflictDate(), ex.getConflictTimeSlotId())));
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
        boolean isDuplicate = ex instanceof DuplicateStudentException;
        StudentValidationErrorDTO detail = (ex instanceof DuplicateStudentException dup)
                ? new StudentValidationErrorDTO(dup.getRow(), dup.getStudentFullName())
                : new StudentValidationErrorDTO(null, null);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(
                        isDuplicate ? ErrorCode.ROSTER_DUPLICATE_STUDENT : ErrorCode.ROSTER_EMPTY,
                        ex.getMessage(),
                        detail));
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
                .body(ApiResponse.error(
                        ErrorCode.ROSTER_COUNT_MISMATCH,
                        ex.getMessage(),
                        new StudentCountMismatchDTO(ex.getExpected(), ex.getActual())));
    }

    // ── Security ─────────────────────────────────────────────────────────────

    /** Returns 429 when an account is temporarily locked after too many failed login attempts. */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        log.warn("Locked account login attempt: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ErrorCode.ACCOUNT_LOCKED,
                        "Account temporarily locked due to too many failed attempts. Try again in 10 minutes."));
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
                .body(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials."));
    }

    /** Handles requests that arrive without an Authorization header or token body; returns 401. */
    @ExceptionHandler(MissingTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingToken(MissingTokenException ex) {
        log.warn("Missing token: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.TOKEN_MISSING, "Authentication token is required."));
    }

    /** Handles tokens that fail signature verification, are expired, or carry the wrong type claim; returns 401. */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.TOKEN_INVALID, "The provided token is invalid or has expired."));
    }

    /** Handles tokens that were explicitly revoked (blacklisted after logout or password reset); returns 401. */
    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenRevoked(TokenRevokedException ex) {
        log.warn("Revoked token used: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.TOKEN_REVOKED, "The provided token has been revoked."));
    }

    /** Safety net for {@link BadCredentialsException} thrown internally by Spring Security's AuthenticationManager; returns 401. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("BadCredentialsException (Spring Security): {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials."));
    }

    /** Handles role-based access control rejections from {@code @PreAuthorize}; returns 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action."));
    }

    // ── Input validation ─────────────────────────────────────────────────────

    /**
     * Handles Bean Validation failures ({@code @Valid} on controller parameters). Always
     * returns a per-field {@link ValidationErrorDTO}, in both {@code dev} and prod — unlike
     * the previous behavior, field names are not hidden in production: the frontend's
     * {@code *_DTO_MAP} already knows every DTO's field names, so withholding them only
     * broke the ability to highlight the offending input.
     *
     * <p>Each annotation's {@code message} is meant to already be an {@link ErrorCode} name
     * (see the enum's Javadoc). If it isn't — e.g. a new constraint was added without a
     * {@code message}, so Hibernate emitted its own default text like {@code "must not be null"}
     * — {@link #resolveFieldErrorCode} degrades to a generic code based on the constraint type,
     * so the client never sees raw Hibernate English.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDTO>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(err.getField(), resolveFieldErrorCode(err));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_FAILED,
                        "Validation failed for one or more fields.",
                        new ValidationErrorDTO(fieldErrors)));
    }

    private static String resolveFieldErrorCode(FieldError err) {
        String msg = err.getDefaultMessage();
        return isErrorCodeName(msg) ? msg : mapConstraintToCode(err.getCode());
    }

    private static boolean isErrorCodeName(String msg) {
        if (msg == null) return false;
        try {
            ErrorCode.valueOf(msg);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String mapConstraintToCode(String constraint) {
        return switch (constraint != null ? constraint : "") {
            case "NotNull", "NotBlank", "NotEmpty" -> ErrorCode.FIELD_REQUIRED.name();
            case "Pattern", "Email", "URL" -> ErrorCode.FIELD_INVALID_FORMAT.name();
            case "Size", "Min", "Max", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero",
                 "Future", "FutureOrPresent", "Past", "PastOrPresent" -> ErrorCode.FIELD_OUT_OF_RANGE.name();
            default -> ErrorCode.VALIDATION_FAILED.name();
        };
    }

    /**
     * Handles method-level validation failures raised by Spring Framework 6.1+'s built-in
     * method validation, which — depending on how the constraint is attached to a
     * {@code @RequestPart}/{@code @RequestParam} parameter — may fire instead of
     * {@link MethodArgumentNotValidException}. Defensive twin of {@link #handleValidation}
     * so multipart binding failures never fall through to the 500 catch-all.
     *
     * <p>Field names come from {@link ParameterValidationResult#getMethodParameter()}, which
     * requires the parameter name to be resolvable (compiled with {@code -parameters}, present
     * here). If it can't be resolved, that entry is skipped rather than emitted with a
     * {@code null} key — better a missing field than a broken map.</p>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDTO>> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            if (field == null) continue;
            String msg = result.getResolvableErrors().isEmpty()
                    ? null
                    : result.getResolvableErrors().get(0).getDefaultMessage();
            fieldErrors.putIfAbsent(field, isErrorCodeName(msg) ? msg : ErrorCode.VALIDATION_FAILED.name());
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_FAILED,
                        "Method validation failed for one or more parameters.",
                        new ValidationErrorDTO(fieldErrors)));
    }

    /** Handles unreadable or malformed JSON request bodies that cannot be deserialized; returns 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.MALFORMED_REQUEST, "Request body is malformed or unreadable."));
    }

    /**
     * Handles a multipart request that is missing a required part (e.g. the {@code file}
     * part of {@code POST /api/v1/reservations/booking}); returns 400 with the part name.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.FILE_PART_MISSING,
                        "Required request part is missing: " + ex.getRequestPartName()));
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
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE, "The uploaded file exceeds the maximum allowed size."));
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
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETERS, message));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /**
     * Known DB unique-constraint names mapped to their {@link ErrorCode}. Only constraints
     * that already carry a stable name in the SQL baseline are listed here — most of this
     * schema's unique constraints are Hibernate-generated hashes (e.g. {@code UK6dotkott2kjsp8vw4d0m25fb7}
     * for {@code users.email}) that a future migration still needs to rename before they can
     * be added. Until then, a race against those columns' pre-check falls through to the
     * generic {@link ErrorCode#DB_CONSTRAINT_VIOLATION} below — worse than a field-specific
     * message, but still a 409 instead of the previous opaque 500.
     */
    private static final Map<String, ErrorCode> KNOWN_CONSTRAINT_CODES = Map.of(
            "uk_reserv_slots_classroom_time", ErrorCode.RESERVATION_SLOT_CONFLICT,
            "uk_reserv_slots_user_time", ErrorCode.RESERVATION_SLOT_CONFLICT
    );

    /** Matches MySQL's {@code Duplicate entry '...' for key 'table.constraint'} (or just {@code 'constraint'}). */
    private static final Pattern MYSQL_DUPLICATE_KEY_PATTERN =
            Pattern.compile("for key '([^']+)'", Pattern.CASE_INSENSITIVE);

    /**
     * Handles unique-constraint races that slip past the service layer's pre-check (two
     * concurrent requests both pass the {@code findByEmail} check before either commits).
     * Declared before {@link #handleDataAccess} so Spring dispatches here first — this is
     * a subtype of {@link DataAccessException}.
     *
     * <p>Parses the constraint name only from MySQL's {@link SQLIntegrityConstraintViolationException}
     * message — this project has a single DB engine (MySQL, see {@code pom.xml}), so there is
     * no cross-driver format to reconcile. Any parse failure — unrecognized format, unmapped
     * constraint — falls back to a generic 409 rather than throwing; the parsing is an
     * opportunistic improvement, never a hard dependency.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ErrorCode code = ErrorCode.DB_CONSTRAINT_VIOLATION;
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLIntegrityConstraintViolationException) {
            String constraint = extractMysqlConstraintName(cause.getMessage());
            if (constraint != null && KNOWN_CONSTRAINT_CODES.containsKey(constraint)) {
                code = KNOWN_CONSTRAINT_CODES.get(constraint);
            }
        }
        log.warn("Data integrity violation resolved to {}: {}", code, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(code, "A database constraint was violated."));
    }

    private static String extractMysqlConstraintName(String message) {
        if (message == null) return null;
        Matcher matcher = MYSQL_DUPLICATE_KEY_PATTERN.matcher(message);
        if (!matcher.find()) return null;
        String key = matcher.group(1);
        int dot = key.lastIndexOf('.');
        return (dot >= 0 ? key.substring(dot + 1) : key).toLowerCase(Locale.ROOT);
    }

    /** Handles Spring Data access failures (query errors, connection issues) not more specifically matched above; returns 500. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "The operation could not be completed. Please try again later."));
    }

    /** Handles low-level JPA/Hibernate persistence exceptions not caught by Spring's exception translation; returns 500. */
    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistence(PersistenceException ex) {
        log.error("JPA persistence error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "The operation could not be completed. Please try again later."));
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
                .body(ApiResponse.error(ex.getCode(), message));
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
                .body(ApiResponse.error(ex.getCode(), message));
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    /**
     * Handles requests to routes that don't exist. Without this handler an unknown route fell
     * through to the {@link Exception} catch-all below, reporting an unmatched URL as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.ENDPOINT_NOT_FOUND, "No handler found for this route."));
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
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, message));
    }
}
