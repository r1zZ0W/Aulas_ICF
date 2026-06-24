package mx.unam.icf.aulas.modules.resources.classrooms.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.resources.classrooms.app.ClassroomService;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomChildrenRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomStatsDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing classroom API endpoints.
 * <p>
 * Read operations are available to any authenticated user.
 * Write and delete operations are restricted to {@code ADMIN} role.
 * All endpoints are exposed under the base path {@code /api/v1/classrooms}.
 */
@RestController
@RequestMapping(value = "/api/v1/classrooms", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ClassroomController implements ResponseHandler {

    private final ClassroomService classroomService;

    /**
     * Returns aggregated classroom statistics for the admin dashboard. Requires ADMIN role.
     * GET /api/v1/classrooms/stats
     *
     * <p>Resolves total, available (active), and not-available (inactive/null) counts in a single
     * database round-trip. Spring MVC matches the literal path {@code /stats} before the template
     * {@code /{uuid}}, so there is no route conflict.</p>
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ClassroomStatsDTO>> stats() {
        return ok(classroomService.getStats());
    }

    /**
     * Retrieves classrooms from the system, paginated.
     * ADMIN users receive all classrooms (active and inactive) for management purposes.
     * MAESTRO users receive only active classrooms (DFR §2.3).
     * GET /api/v1/classrooms[?search=text&page=0&size=20&sort=name&direction=asc]
     *
     * <p>When {@code search} is provided, a case-insensitive {@code LIKE} filter is applied
     * over {@code name} and {@code description}. {@code totalElements} reflects the filtered count.
     * Allowed sort fields: {@code createdAt}, {@code name}, {@code capacity}.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultDTO<ClassroomResponseDTO>>> findAll(
            @AuthenticationPrincipal UserDetailsImp principal,
            @RequestParam(value = "search", required = false) String search,
            @SortWhitelist(
                    value = {"createdAt", "name", "capacity"},
                    defaultSort = "name",
                    defaultDirection = "asc")
            PageCriteria criteria) {
        if ("ADMIN".equals(principal.getRoleName()))
            return ok(classroomService.findAll(search, criteria.toPageable()));
        return ok(classroomService.findAllActive(search, criteria.toPageable()));
    }

    /**
     * Retrieves a specific classroom by its public UUID.
     * GET /api/v1/classrooms/{uuid}
     *
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(classroomService.findByUuid(uuid));
    }

    /**
     * Creates a new classroom in the system. Requires ADMIN role.
     * POST /api/v1/classrooms
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> save(@Valid @RequestBody ClassroomRequestDTO dto) {
        return created(classroomService.save(dto));
    }

    /**
     * Updates an existing classroom with new data. Requires ADMIN role.
     * PUT /api/v1/classrooms/{uuid}
     *
     * @throws DomainException           when the UUID is not valid
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ClassroomRequestDTO dto
    ) {
        return ok(classroomService.update(uuid, dto));
    }

    /**
     * Toggles the active status of a classroom. Requires ADMIN role.
     *
     * <p>Flips {@code isActive}: an active classroom becomes inactive (hidden from the
     * Maestro catalog and unable to accept new reservations), and an inactive or
     * null-status classroom becomes active. Child classrooms are not affected —
     * the parent–child link is preserved regardless of the parent's status.
     * The classroom is never physically removed (DFR NFR / LFTAIP).</p>
     *
     * PATCH /api/v1/classrooms/{uuid}/toggle-status
     *
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when the classroom is not found
     * @return the updated classroom with its new {@code isActive} value
     */
    @PatchMapping("/{uuid}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> toggleStatus(@PathVariable UUID uuid) {
        return ok(classroomService.toggleStatus(uuid));
    }

    /**
     * Permanently deletes a classroom and all its dependents (cascade).
     *
     * <p>This operation removes the classroom row together with all associated
     * {@code ReservSlot}, {@code ReservInstance}, {@code ClassroomResource} rows
     * and any orphan {@code ReservationGroup}s (groups whose every instance was in
     * this classroom). All data loss is irreversible. See
     * {@link ClassroomService#delete} for the full deletion order and the
     * ⚠️ architecture warning regarding the NFR trade-off.</p>
     *
     * DELETE /api/v1/classrooms/{uuid}
     *
     * @param uuid public UUID of the classroom to delete
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID uuid) {
        classroomService.delete(uuid);
        return ok("Classroom deleted.");
    }

    /**
     * Atomically assigns a set of direct child classrooms to the given parent. Requires ADMIN role.
     *
     * <p>The body contains the <em>desired full set</em> of children. Classrooms in the list
     * are linked; classrooms currently linked but absent are unlinked. An empty list removes
     * all children. All changes are applied in a single transaction.</p>
     *
     * PUT /api/v1/classrooms/{uuid}/children
     *
     * @param uuid public UUID of the parent classroom
     * @param dto  body containing the desired list of child UUIDs
     * @throws ResourceNotFoundException when the parent classroom is not found
     * @throws DomainException           when a child UUID is missing, inactive, or would form a cycle
     */
    @PutMapping("/{uuid}/children")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setChildren(
            @PathVariable UUID uuid,
            @Valid @RequestBody ClassroomChildrenRequestDTO dto) {
        classroomService.setChildren(uuid, dto.childUuids());
        return ok("Children updated successfully");
    }
}
