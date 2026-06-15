package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.reservations.instances.app.ReservInstanceService;
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
 * REST controller for managing classroom reservation instance endpoints.
 *
 * <p>Read operations are available to any authenticated user.
 * Approval, rejection, admin cancellation, and reassignment require the {@code ADMIN} role.
 * All endpoints are exposed under the base path {@code /api/v1/reservations}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReservInstanceController implements ResponseHandler {

    private final ReservInstanceService service;

    /**
     * Retrieves all reservation instances in the system.
     * GET /api/v1/reservations
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> findAll() {
        return ok(service.findAll());
    }

    /**
     * Retrieves all reservation instances awaiting review. Requires ADMIN role.
     * GET /api/v1/reservations/pending
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> findPending() {
        return ok(service.findPending());
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
     * Retrieves all reservation instances for a specific user.
     * ADMIN users may query any user; a Maestro may only query their own reservations.
     * GET /api/v1/reservations/user/{userUuid}
     *
     * @throws AccessDeniedException when a non-admin attempts to view another user's reservations
     */
    @GetMapping("/user/{userUuid}")
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> findByUser(
            @PathVariable UUID userUuid,
            @AuthenticationPrincipal UserDetailsImp principal) {
        if (!"ADMIN".equals(principal.getRoleName()) && !userUuid.equals(principal.getUuid()))
            throw new AccessDeniedException("You can only view your own reservations");
        return ok(service.findByUser(userUuid));
    }

    /**
     * Returns approved reservations for a classroom within a date range (availability calendar).
     * GET /api/v1/reservations/availability?classroomUuid=...&from=...&to=...
     */
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<ReservInstanceResponseDTO>>> findAvailability(
            @RequestParam UUID classroomUuid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ok(service.findAvailability(classroomUuid, from, to));
    }

    /**
     * Creates a new reservation instance with status {@code PENDIENTE}.
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
     * Approves a pending reservation. Requires ADMIN role.
     * PATCH /api/v1/reservations/{uuid}/approve
     */
    @PatchMapping("/{uuid}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> approve(@PathVariable UUID uuid) {
        return ok(service.approve(uuid));
    }

    /**
     * Rejects a pending reservation. Requires ADMIN role.
     * PATCH /api/v1/reservations/{uuid}/reject
     */
    @PatchMapping("/{uuid}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> reject(@PathVariable UUID uuid) {
        return ok(service.reject(uuid));
    }

    /**
     * Cancels a reservation as the owning teacher.
     * Only the Maestro who owns the reservation may cancel it (DFR §4.2).
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
     * Reassigns an approved reservation to a different classroom. Requires ADMIN role.
     * PATCH /api/v1/reservations/{uuid}/reassign?newClassroomUuid=...
     */
    @PatchMapping("/{uuid}/reassign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReservInstanceResponseDTO>> reassign(
            @PathVariable UUID uuid,
            @RequestParam UUID newClassroomUuid
    ) {
        return ok(service.reassign(uuid, newClassroomUuid));
    }
}
