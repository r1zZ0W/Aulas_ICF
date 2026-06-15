package mx.unam.icf.aulas.modules.resources.equipment.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.resources.equipment.app.ResourceService;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing the equipment resource catalog.
 *
 * <p>Read operations are available to any authenticated user.
 * Write and delete operations require the {@code ADMIN} role.
 * All endpoints are exposed under {@code /api/v1/resources}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/resources", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ResourceController implements ResponseHandler {

    private final ResourceService service;

    /**
     * Retrieves all equipment resources in the catalog.
     * GET /api/v1/resources
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ResourceDTO>>> findAll() {
        return ok(service.findAll());
    }

    /**
     * Retrieves a single equipment resource by its internal ID.
     * GET /api/v1/resources/{id}
     *
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the resource is not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResourceDTO>> findById(@PathVariable Integer id) {
        return ok(service.findById(id));
    }

    /**
     * Creates a new equipment resource in the catalog. Requires ADMIN role.
     * POST /api/v1/resources
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ResourceDTO>> save(@RequestBody ResourceDTO dto) {
        return created(service.save(dto));
    }

    /**
     * Updates an existing equipment resource. Requires ADMIN role.
     * PUT /api/v1/resources/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ResourceDTO>> update(@PathVariable Integer id, @RequestBody ResourceDTO dto) {
        return ok(service.update(id, dto));
    }

    /**
     * Deletes an equipment resource from the catalog. Requires ADMIN role.
     * DELETE /api/v1/resources/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ok("Equipment resource deleted successfully");
    }
}
