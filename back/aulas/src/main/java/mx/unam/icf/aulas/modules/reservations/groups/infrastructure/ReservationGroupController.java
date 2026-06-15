package mx.unam.icf.aulas.modules.reservations.groups.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.reservations.groups.app.ReservationGroupService;
import mx.unam.icf.aulas.modules.reservations.groups.app.dtos.ReservationGroupRequestDTO;
import mx.unam.icf.aulas.modules.reservations.groups.app.dtos.ReservationGroupResponseDTO;
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

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing reservation group API endpoints.
 *
 * <p>A reservation group defines a recurring weekly pattern for a user within a semester.
 * All endpoints are exposed under the base path {@code /api/v1/reservation-groups}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/reservation-groups", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReservationGroupController implements ResponseHandler {

    private final ReservationGroupService service;

    /**
     * Retrieves all reservation groups. Requires ADMIN role.
     * GET /api/v1/reservation-groups
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReservationGroupResponseDTO>>> findAll() {
        return ok(service.findAll());
    }

    /**
     * Retrieves a single reservation group by its public UUID.
     * GET /api/v1/reservation-groups/{uuid}
     *
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the group is not found
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<ReservationGroupResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(service.findByUuid(uuid));
    }

    /**
     * Retrieves all reservation groups for a specific user.
     * ADMIN users may query any user; a Maestro may only query their own groups.
     * GET /api/v1/reservation-groups/user?userUuid={uuid}
     *
     * @throws AccessDeniedException when a non-admin attempts to view another user's groups
     */
    @GetMapping("/user")
    public ResponseEntity<ApiResponse<List<ReservationGroupResponseDTO>>> findByUser(
            @RequestParam UUID userUuid,
            @AuthenticationPrincipal UserDetailsImp principal) {
        if (!"ADMIN".equals(principal.getRoleName()) && !userUuid.equals(principal.getUuid()))
            throw new AccessDeniedException("You can only view your own reservation groups");
        return ok(service.findByUser(userUuid));
    }

    /**
     * Creates a new reservation group.
     * A Maestro may only create groups for themselves; ADMIN may create for any user.
     * POST /api/v1/reservation-groups
     *
     * @throws AccessDeniedException when a Maestro attempts to create a group for another user
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationGroupResponseDTO>> save(
            @Valid @RequestBody ReservationGroupRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImp principal) {
        boolean isAdmin = "ADMIN".equals(principal.getRoleName());
        return created(service.save(dto, principal.getUuid(), isAdmin));
    }

    /**
     * Cancels an active reservation group. ADMIN may cancel any group; a Maestro may
     * only cancel their own groups.
     * PATCH /api/v1/reservation-groups/{uuid}/cancel
     *
     * @throws AccessDeniedException when a Maestro attempts to cancel another user's group
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException when the group is already cancelled
     */
    @PatchMapping("/{uuid}/cancel")
    public ResponseEntity<ApiResponse<ReservationGroupResponseDTO>> cancel(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal UserDetailsImp principal) {
        boolean isAdmin = "ADMIN".equals(principal.getRoleName());
        return ok(service.cancel(uuid, principal.getUuid(), isAdmin));
    }
}
