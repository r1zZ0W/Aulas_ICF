package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteriaArgumentResolver;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.reservations.instances.app.ReservInstanceService;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.BookingRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReassignRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceResponseDTO;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for classroom reservation instance endpoints.
 *
 * <p>Reservations are created in the {@link mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus#ACTIVA}
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
     * Retrieves a paginated list of reservation instances.
     * GET /api/v1/reservations[?page=0&size=20&sort=date&direction=desc]
     *
     * <p>Allowed sort fields: {@code createdAt}, {@code date}, {@code status}.</p>
     *
     * @param criteria pagination and sorting criteria, resolved and validated by {@link PageCriteriaArgumentResolver}
     * @return paginated list of all reservation instances
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultDTO<ReservInstanceResponseDTO>>> findAll(
            @SortWhitelist(
                    value = {"createdAt", "date", "status"},
                    defaultSort = "date",
                    defaultDirection = "desc")
            PageCriteria criteria) {
        return ok(service.findAll(criteria.toPageable()));
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
     * Retrieves reservation instances for a specific user, paginated.
     * ADMIN users may query any user; a Maestro may only query their own reservations.
     * GET /api/v1/reservations/user/{userUuid}[?page=0&size=20&sort=date&direction=desc]
     *
     * <p>Allowed sort fields: {@code createdAt}, {@code date}, {@code status}.</p>
     *
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
            PageCriteria criteria) {
        if (!"ADMIN".equals(principal.getRoleName()) && !userUuid.equals(principal.getUuid()))
            throw new AccessDeniedException("You can only view your own reservations");
        return ok(service.findByUser(userUuid, criteria.toPageable()));
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
     * Atomically creates a reservation group and all its instances in a single transaction.
     * POST /api/v1/reservations/booking
     *
     * <p>The frontend sends the booking intent once (classroom, time block, optional recurrence);
     * the backend generates every date occurrence. No client-side weekly loop is needed.</p>
     *
     * @return list of created instances (one per target date)
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException             on rule violations (400)
     * @throws mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException on slot conflict (409)
     */
    @PostMapping("/booking")
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> createBooking(
            @Valid @RequestBody BookingRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImp principal) {
        return created(service.createBooking(dto, principal.getUuid()));
    }

    /**
     * Creates a new reservation instance with status {@code ACTIVA}.
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
