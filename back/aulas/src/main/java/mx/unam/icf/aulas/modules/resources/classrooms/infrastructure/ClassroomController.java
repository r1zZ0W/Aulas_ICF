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
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * Retrieves classrooms from the system, paginated.
     * ADMIN users receive all classrooms (active and inactive) for management purposes.
     * MAESTRO users receive only active classrooms (DFR §2.3).
     * GET /api/v1/classrooms[?page=0&size=20&sort=name&direction=asc]
     *
     * <p>Allowed sort fields: {@code createdAt}, {@code name}, {@code capacity}.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultDTO<ClassroomResponseDTO>>> findAll(
            @AuthenticationPrincipal UserDetailsImp principal,
            @SortWhitelist(
                    value = {"createdAt", "name", "capacity"},
                    defaultSort = "name",
                    defaultDirection = "asc")
            PageCriteria criteria) {
        if ("ADMIN".equals(principal.getRoleName()))
            return ok(classroomService.findAll(criteria.toPageable()));
        return ok(classroomService.findAllActive(criteria.toPageable()));
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
     * Deactivates a classroom (soft-delete) by its public UUID. Requires ADMIN role.
     * The classroom is marked inactive and hidden from Maestro catalog but is never
     * physically removed, preserving existing reservation history (DFR NFR / LFTAIP).
     * DELETE /api/v1/classrooms/{uuid}
     *
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteByUuid(@PathVariable UUID uuid) {
        classroomService.deleteByUuid(uuid);
        return ok("Classroom deactivated successfully");
    }
}
