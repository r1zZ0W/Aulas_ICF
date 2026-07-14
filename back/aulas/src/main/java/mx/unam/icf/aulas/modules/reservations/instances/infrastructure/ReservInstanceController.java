package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteriaArgumentResolver;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.reservations.instances.app.ReservInstanceService;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.BookingRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReassignRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceFilter;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceResponseDTO;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for classroom reservation instance endpoints.
 *
 * <p>Reservations are created in the {@link ReservInstanceStatus#ACTIVE}
 * state and occupy the classroom immediately — there is no approval step.
 * Cancellation and reassignment require the ADMIN role (except user self-cancellation).
 * All endpoints are exposed under the base path {@code /api/v1/reservations}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReservInstanceController implements ResponseHandler {

    private final ReservInstanceService service;

    /**
     * Retrieves a filtered, paginated list of all reservation instances.
     * GET /api/v1/reservations[?page=0&size=20&sort=date&direction=desc&search=...&status=...&reassigned=...&classroomId=...&from=...&to=...]
     *
     * <p>Allowed sort fields: {@code createdAt}, {@code date}, {@code status}.</p>
     *
     * <p>All filter parameters are optional and combined with AND when present:</p>
     * <ul>
     *   <li>{@code search} — case-insensitive LIKE over classroom name and user full name
     *       (min. 3 chars recommended; full table scan below that threshold)</li>
     *   <li>{@code status} — exact match; invalid value → 400</li>
     *   <li>{@code reassigned} — {@code true} = only admin-reassigned instances;
     *       {@code false} = only never-reassigned instances; omitted = no restriction.
     *       Combine with {@code status=ACTIVE} for clean "Activa/Reasignada" partition.
     *       Non-boolean value (e.g. {@code reassigned=foo}) → 400.</li>
     *   <li>{@code classroomId} — equality on classroom UUID</li>
     *   <li>{@code from} / {@code to} — inclusive date range (ISO format {@code yyyy-MM-dd})</li>
     * </ul>
     *
     * @param criteria    pagination and sorting criteria, resolved by {@link PageCriteriaArgumentResolver}
     * @param search      optional case-insensitive search term (classroom name or user name)
     * @param status      optional status filter
     * @param reassigned  optional reassignment flag filter ({@code true}/{@code false}; omit for no restriction)
     * @param classroomId optional classroom UUID filter
     * @param from        optional start date (inclusive)
     * @param to          optional end date (inclusive)
     * @return paginated list of reservation instances matching the filters; {@code totalElements}
     *         reflects the filtered count, not the global total
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultDTO<ReservInstanceResponseDTO>>> findAll(
            @SortWhitelist(
                    value = {"createdAt", "date", "status"},
                    defaultSort = "date",
                    defaultDirection = "desc")
            PageCriteria criteria,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ReservInstanceStatus status,
            @RequestParam(required = false) Boolean reassigned,
            @RequestParam(required = false) UUID classroomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReservInstanceFilter filter = new ReservInstanceFilter(search, status, reassigned, classroomId, from, to, null);
        return ok(service.findAll(filter, criteria.toPageable()));
    }

    /**
     * Retrieves a single reservation instance by its public UUID.
     * GET /api/v1/reservations/{uuid}
     *
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the instance is not found
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(service.findByUuid(uuid));
    }

    /**
     * Retrieves filtered, paginated reservation instances for a specific user.
     * ADMIN users may query any user; a Maestro may only query their own reservations.
     * GET /api/v1/reservations/user/{userUuid}[?page=0&size=20&sort=date&direction=desc&search=...&status=...&reassigned=...&classroomId=...&from=...&to=...]
     *
     * <p>Allowed sort fields: {@code createdAt}, {@code date}, {@code status}.</p>
     *
     * <p>Same filter parameters as {@link #findAll} are supported, including
     * {@code reassigned} and {@code classroomId}. The {@code userUuid} path variable is
     * injected into the filter by the service layer via
     * {@link mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceFilter#withUser}
     * — it is not a request parameter.</p>
     *
     * @param userUuid    public UUID of the target user (path variable)
     * @param principal   authenticated user (used for access control)
     * @param criteria    pagination and sorting criteria
     * @param search      optional case-insensitive search term
     * @param status      optional status filter
     * @param reassigned  optional reassignment flag filter ({@code true}/{@code false}; omit for no restriction)
     * @param classroomId optional classroom UUID filter
     * @param from        optional start date (inclusive)
     * @param to          optional end date (inclusive)
     * @throws AccessDeniedException when a non-admin attempts to view another user's reservations
     */
    @GetMapping("/user/{userUuid}")
    public ResponseEntity<ApiResponse<PagedResultDTO<ReservInstanceResponseDTO>>> findByUser(
            @PathVariable UUID userUuid,
            @AuthenticationPrincipal UserDetailsImp principal,
            @SortWhitelist(
                    value = {"createdAt", "date", "status"},
                    defaultSort = "date",
                    defaultDirection = "desc")
            PageCriteria criteria,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ReservInstanceStatus status,
            @RequestParam(required = false) Boolean reassigned,
            @RequestParam(required = false) UUID classroomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!"ADMIN".equals(principal.getRoleName()) && !userUuid.equals(principal.getUuid()))
            throw new AccessDeniedException("You can only view your own reservations");
        ReservInstanceFilter filter = new ReservInstanceFilter(search, status, reassigned, classroomId, from, to, null);
        return ok(service.findByUser(userUuid, filter, criteria.toPageable()));
    }

    /**
     * Returns active reservations within a date range (availability calendar).
     * GET /api/v1/reservations/availability?from=...&to=...[&classroomUuid=...]
     *
     * <p>When {@code classroomUuid} is omitted the response includes active instances for
     * <em>all</em> classrooms — used when the calendar displays every room at once.</p>
     */
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> findAvailability(
            @RequestParam(required = false) UUID classroomUuid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ok(service.findAvailability(classroomUuid, from, to));
    }

    /**
     * Returns the time slots currently available (not yet booked) for a classroom on a given date.
     * GET /api/v1/reservations/available-slots?classroomUuid=...&date=...
     *
     * <p>Computed as {@code full catalog − occupied slots}; ordered by {@code startTime} ascending
     * so the frontend can walk chronological contiguity when deriving "end time" options.</p>
     *
     * @param classroomUuid public UUID of the classroom
     * @param date          date to check availability for (ISO {@code yyyy-MM-dd})
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the classroom is not found
     */
    @GetMapping("/available-slots")
    public ResponseEntity<ApiResponse<List<TimeSlotDTO>>> findAvailableSlots(
            @RequestParam UUID classroomUuid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ok(service.findAvailableSlots(classroomUuid, date));
    }

    /**
     * Atomically creates a reservation group and all its instances in a single transaction.
     * POST /api/v1/reservations/booking (multipart/form-data)
     *
     * <p>The request carries two parts: {@code data} — the JSON booking intent (classroom,
     * time block, optional recurrence) — and {@code file} — the mandatory student roster
     * ({@code .xlsx}). The backend validates the roster (including that its student count
     * equals {@code attendeeCount}) <em>before</em> creating anything; the frontend never
     * parses the Excel. The backend generates every date occurrence — no client-side loop.</p>
     *
     * <p>{@code dto.userUuid()} lets the request book on behalf of a different user
     * (teacher or admin) instead of the caller — but only an ADMIN may set it to anything
     * other than their own UUID; any other caller supplying someone else's UUID is rejected
     * before the service layer is even invoked.</p>
     *
     * @param dto       booking intent, sent as the {@code data} part with {@code application/json} type
     * @param file      the roster {@code .xlsx}, sent as the {@code file} part
     * @param principal authenticated user making the request
     * @return list of created instances (one per target date)
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException             on rule violations (400)
     * @throws mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException when the file is not a valid .xlsx (400)
     * @throws mx.unam.icf.aulas.modules.reservations.students.app.exceptions.StudentCountMismatchException when roster size != attendeeCount (422)
     * @throws mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException on slot conflict (409)
     * @throws AccessDeniedException when a non-admin supplies a {@code userUuid} other than their own
     */
    @PostMapping(value = "/booking", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> createBooking(
            @Valid @RequestPart("data") BookingRequestDTO dto,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImp principal) {
        // Only an admin may book on behalf of someone else. Comparing against the caller's
        // own UUID (rather than just null-checking userUuid) keeps this resilient to a future
        // frontend that always sends userUuid explicitly (even when it's the caller's own) —
        // the real rule is "can't impersonate another user", not "can't send this field".
        if (dto.userUuid() != null
                && !dto.userUuid().equals(principal.getUuid())
                && !"ADMIN".equals(principal.getRoleName())
        )
            throw new AccessDeniedException("Only administrators can create a reservation on behalf of another user");
        return created(service.createBooking(dto, readBytes(file), principal.getUuid()));
    }

    /**
     * Reads the multipart roster into memory, translating a transport-level
     * {@link IOException} into the same 400 response used for structurally invalid files
     * (mirrors {@code StudentListController#readBytes}).
     */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidExcelFileException("Could not read the uploaded file.", e);
        }
    }

    /**
     * Creates a new reservation instance with status {@code ACTIVE}.
     * The classroom is occupied immediately — no admin approval is required.
     * The authenticated user must own the reservation group referenced in the payload.
     * POST /api/v1/reservations
     *
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException when any business rule is violated
     * @throws AccessDeniedException when the group does not belong to the authenticated user
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> save(
            @Valid @RequestBody ReservInstanceRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImp principal) {
        return created(service.save(dto, principal.getUuid()));
    }

    /**
     * Cancels a reservation as the owning teacher.
     * Only the Maestro who owns the reservation may cancel it.
     * PATCH /api/v1/reservations/{uuid}/cancel
     *
     * @throws AccessDeniedException when the authenticated user does not own the reservation
     */
    @PatchMapping("/{uuid}/cancel")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> cancelByUser(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal UserDetailsImp principal) {
        return ok(service.cancelByUser(uuid, principal.getUuid()));
    }

    /**
     * Cancels a reservation as an administrator. Requires ADMIN role.
     * PATCH /api/v1/reservations/{uuid}/cancel-admin
     */
    @PatchMapping("/{uuid}/cancel-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> cancelByAdmin(@PathVariable UUID uuid) {
        return ok(service.cancelByAdmin(uuid));
    }

    /**
     * Reassigns an active reservation to a different classroom and/or time-slot block.
     * Requires ADMIN role.
     * PATCH /api/v1/reservations/{uuid}/reassign
     *
     * <p>At least one of {@code newClassroomUuid} or {@code newTimeSlotIds} must be provided.</p>
     */
    @PatchMapping("/{uuid}/reassign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> reassign(
            @PathVariable UUID uuid,
            @RequestBody ReassignRequestDTO dto
    ) {
        return ok(service.reassign(uuid, dto));
    }
}
