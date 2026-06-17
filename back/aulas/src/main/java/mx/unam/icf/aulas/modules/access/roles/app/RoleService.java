package mx.unam.icf.aulas.modules.access.roles.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.access.roles.app.dtos.RoleDTO;
import mx.unam.icf.aulas.modules.access.roles.app.mappers.RoleMapper;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for the Role catalog.
 *
 * <p>Roles are static reference data seeded at startup and never modified at runtime.
 * This service exposes a single read operation used by administrative endpoints to
 * populate role selectors on the frontend.</p>
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    /**
     * Returns all roles in the system as DTOs (id + name).
     *
     * @return list of {@link RoleDTO} ordered by insertion order (ADMIN first, then MAESTRO)
     */
    public List<RoleDTO> findAll() {
        return roleMapper.toDtoList(roleRepository.findAll());
    }
}
