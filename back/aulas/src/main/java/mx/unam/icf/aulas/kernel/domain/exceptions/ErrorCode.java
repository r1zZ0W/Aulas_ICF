package mx.unam.icf.aulas.kernel.domain.exceptions;

/**
 * Stable, machine-readable identifier for every error the API can return.
 *
 * <p>This is the contract the frontend consumes to render a Spanish message — it maps
 * every value here to human text in a single catalog ({@code src/errors/errorCatalog.js}).
 * The English strings passed alongside a code (via {@code DomainException} messages or
 * {@code @NotBlank(message = ...)} literals) are for logs and debugging only; the UI never
 * shows them directly.</p>
 *
 * <p>Three codes — {@link #FIELD_REQUIRED}, {@link #FIELD_INVALID_FORMAT} and
 * {@link #FIELD_OUT_OF_RANGE} — serve two roles at once: they are the automatic fallback
 * {@code GlobalExceptionHandler} degrades to when a bean-validation constraint's
 * {@code defaultMessage} isn't a recognized code, and they are also used <em>deliberately</em>
 * as the literal {@code message} on constraints whose text carries no meaning beyond
 * "required" / "wrong format" / "out of range" — most {@code @NotBlank}, {@code @Size} and
 * {@code @Min} constraints across the DTOs. A constraint only gets its own dedicated code when
 * the message says something a generic code can't (e.g. {@link #USER_EMAIL_DOMAIN_INVALID}).</p>
 */
public enum ErrorCode {

    // ── Generic / fallback ──────────────────────────────────────────────────
    INTERNAL_ERROR,
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_PARAMETERS,
    ENDPOINT_NOT_FOUND,
    DB_CONSTRAINT_VIOLATION,
    CONTRACT_MISMATCH,
    /** Degraded code for a bean-validation constraint whose message isn't a known ErrorCode. */
    FIELD_REQUIRED,
    FIELD_INVALID_FORMAT,
    FIELD_OUT_OF_RANGE,

    // ── Auth ─────────────────────────────────────────────────────────────────
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,
    RATE_LIMITED,
    TOKEN_MISSING,
    TOKEN_INVALID,
    TOKEN_REVOKED,
    TOKEN_WRONG_TYPE,
    ACCESS_DENIED,

    // ── Users / roles ────────────────────────────────────────────────────────
    USER_NOT_FOUND,
    USER_EMAIL_TAKEN,
    USER_USERNAME_TAKEN,
    USER_CANNOT_DELETE_SELF,
    USER_PASSWORD_CHANGE_FORBIDDEN,
    USER_SELF_ROLE_EDIT_FORBIDDEN,
    USER_MATRICULA_GENERATION_FAILED,
    USER_EMAIL_DOMAIN_INVALID,
    USER_PASSWORD_WEAK,
    USER_USERNAME_CHARSET_INVALID,
    PERSON_NAME_CHARSET_INVALID,
    ROLE_NOT_FOUND,

    // ── Semesters ────────────────────────────────────────────────────────────
    SEMESTER_NOT_FOUND,
    SEMESTER_NAME_TAKEN,
    SEMESTER_NO_ACTIVE,
    SEMESTER_END_BEFORE_START,
    SEMESTER_DURATION_TOO_LONG,
    SEMESTER_START_IN_PAST,
    SEMESTER_END_IN_PAST,

    // ── Classrooms / resources / allocations ────────────────────────────────
    CLASSROOM_NOT_FOUND,
    CLASSROOM_NAME_TAKEN,
    CLASSROOM_NAME_CHARSET_INVALID,
    CLASSROOM_IMAGE_URL_INVALID,
    CLASSROOM_INACTIVE,
    CLASSROOM_CHILDREN_NOT_FOUND,
    CLASSROOM_CHILD_INACTIVE,
    CLASSROOM_CYCLE_DETECTED,
    RESOURCE_NOT_FOUND,
    RESOURCE_NAME_TAKEN,
    ALLOCATION_NOT_FOUND,
    ALLOCATION_QUANTITY_INVALID,

    // ── Reservations ─────────────────────────────────────────────────────────
    RESERVATION_NOT_FOUND,
    RESERVATION_GROUP_NOT_FOUND,
    RESERVATION_GROUP_ALREADY_CANCELLED,
    RESERVATION_GROUP_SCHEDULE_MISMATCH,
    TIMESLOT_NOT_FOUND,
    RESERVATION_SLOT_CONFLICT,
    RESERVATION_OWN_CONFLICT,
    RESERVATION_DATE_IN_PAST,
    RESERVATION_ON_SUNDAY,
    RESERVATION_DATE_OUT_OF_SEMESTER,
    RESERVATION_NO_VALID_DATES,
    RESERVATION_TOO_SOON,
    RESERVATION_ALREADY_CANCELLED,
    RESERVATION_NOT_ACTIVE,
    REASSIGN_TARGET_REQUIRED,
    REASSIGN_CLASSROOM_INACTIVE,

    // ── Student roster ───────────────────────────────────────────────────────
    ROSTER_NOT_FOUND,
    ROSTER_EMPTY,
    ROSTER_DUPLICATE_STUDENT,
    ROSTER_COUNT_MISMATCH,
    ROSTER_FILE_INVALID,
    ROSTER_FILE_UNREADABLE,
    ROSTER_PDF_FAILED,

    // ── Infra ────────────────────────────────────────────────────────────────
    FILE_TOO_LARGE,
    FILE_PART_MISSING,
    FILE_STORAGE_ERROR,
    MAIL_SENDING_ERROR,
    INVALID_SORT_FIELD
}
