package mx.unam.icf.aulas.modules.resources.allocations.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.resources.allocations.app.ClassroomResourceService;
import mx.unam.icf.aulas.modules.resources.allocations.app.dtos.ClassroomResourceRequestDTO;
import mx.unam.icf.aulas.modules.resources.allocations.app.dtos.ClassroomResourceResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing classroom–equipment allocations.
 *
 * <p>Endpoints are nested under {@code /api/v1/classrooms/{classroomUuid}/resources}
 * to reflect the ownership relationship between classrooms and their allocated equipment.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/classrooms/{classroomUuid}/resources", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ClassroomResourceController implements ResponseHandler {

    private final ClassroomResourceService service;

    /**
     * Retrieves all equipment allocations for a classroom.
     * GET /api/v1/classrooms/{classroomUuid}/resources
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClassroomResourceResponseDTO>>> findByClassroom(
            @PathVariable UUID classroomUuid) {
        return ok(service.findByClassroom(classroomUuid));
    }

    /**
     * Assigns or updates an equipment allocation for a classroom. Requires ADMIN role.
     * POST /api/v1/classrooms/{classroomUuid}/resources
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ClassroomResourceResponseDTO>> save(
            @PathVariable UUID classroomUuid,
            @RequestBody ClassroomResourceRequestDTO dto) {
        return created(service.save(classroomUuid, dto));
    }

    /**
     * Removes an equipment allocation from a classroom. Requires ADMIN role.
     * DELETE /api/v1/classrooms/{classroomUuid}/resources/{resourceId}
     */
    @DeleteMapping("/{resourceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID classroomUuid,
            @PathVariable Integer resourceId) {
        service.delete(classroomUuid, resourceId);
        return ok("Equipment allocation removed successfully");
    }
}
