package mx.unam.icf.aulas.modules.access.roles.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.roles.app.RoleService;
import mx.unam.icf.aulas.modules.access.roles.app.dtos.RoleDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Role catalog.
 *
 * <p>Exposes read-only access to the roles catalog so administrative UIs can
 * populate role selectors without hard-coding role IDs on the client side.
 * All endpoints are restricted to the {@code ADMIN} role and served under
 * {@code /api/v1/roles}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RoleController implements ResponseHandler {

    private final RoleService roleService;

    /**
     * Returns all available roles (id + name).
     * GET /api/v1/roles
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> findAll() {
        return ok(roleService.findAll());
    }
}
