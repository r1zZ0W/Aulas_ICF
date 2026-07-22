package mx.unam.icf.aulas.modules.resources.equipment.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceRequestDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceResponseDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceStatsDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.mappers.ResourceMapper;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import mx.unam.icf.aulas.modules.resources.equipment.infrastructure.ResourceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service managing the global equipment resource catalog.
 *
 * <p>Administrators may create, edit, and delete resource types. Each resource tracks
 * only a global {@code quantity} — this module deliberately does not implement
 * per-unit availability, status, or usage tracking (left for a future iteration).</p>
 *
 * <p><strong>Cascade on delete.</strong> {@link #delete} performs a plain
 * {@code repository.delete}; the removal of any {@code classroom_resources} rows
 * that reference the deleted resource is handled entirely by the database via
 * {@code ON DELETE CASCADE} on the {@code resource_id} foreign key (see
 * {@code docs/migration_v1.5__resource_uuid_quantity.sql}). No manual
 * {@code @Modifying} cleanup query is issued here — mixing both strategies would
 * duplicate the cascade and risk desynchronizing Hibernate's persistence context
 * if allocation rows were already loaded in the same transaction.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository repository;
    private final ResourceMapper mapper;

    /**
     * Returns a page of equipment resources in the catalog.
     *
     * <p>When {@code search} is provided and non-blank, a case-insensitive {@code LIKE}
     * filter is applied over {@code name} and {@code description}. When {@code search} is
     * {@code null} or blank the full catalog is returned.</p>
     *
     * @param search   optional free-text filter (may be {@code null})
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ResourceResponseDTO> findAll(String search, Pageable pageable) {
        var page = (search != null && !search.isBlank())
                ? repository.search(search.trim(), pageable)
                : repository.findAll(pageable);
        return PageMapper.toDto(page, mapper::toDtoList);
    }

    /**
     * Returns aggregated resource statistics for the admin dashboard.
     *
     * @return a {@link ResourceStatsDTO} with the current counts
     */
    @Transactional(readOnly = true)
    public ResourceStatsDTO getStats() {
        return repository.fetchStats();
    }

    /**
     * Returns a single equipment resource by its public UUID.
     *
     * @param uuid public UUID of the resource
     * @throws ResourceNotFoundException when no resource matches the given UUID
     */
    @Transactional(readOnly = true)
    public ResourceResponseDTO findByUuid(UUID uuid) {
        return mapper.toDto(
            repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + uuid))
        );
    }

    /**
     * Creates a new equipment resource in the catalog. Requires ADMIN role.
     *
     * @param dto creation payload
     * @throws DomainException when a resource with the same name already exists
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourceResponseDTO save(ResourceRequestDTO dto) {
        if (repository.findByName(dto.name()).isPresent())
            throw new DomainException("An equipment resource with that name already exists: " + dto.name());

        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    /**
     * Updates an existing equipment resource. Requires ADMIN role.
     *
     * @param uuid public UUID of the resource to update
     * @param dto  update payload
     * @throws ResourceNotFoundException when no resource matches the given UUID
     * @throws DomainException           when the new name is already taken by another resource
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourceResponseDTO update(UUID uuid, ResourceRequestDTO dto) {
        Resource resource = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + uuid));

        if (!resource.getName().equals(dto.name()) && repository.findByName(dto.name()).isPresent())
            throw new DomainException("An equipment resource with that name already exists: " + dto.name());

        mapper.updateEntityFromDto(dto, resource);
        return mapper.toDto(repository.save(resource));
    }

    /**
     * Deletes an equipment resource from the catalog. Requires ADMIN role.
     *
     * <p>Any classroom allocations referencing this resource are removed by the
     * database's {@code ON DELETE CASCADE} constraint — see the class-level Javadoc.</p>
     *
     * @param uuid public UUID of the resource to delete
     * @throws ResourceNotFoundException when no resource matches the given UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID uuid) {
        Resource resource = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + uuid));
        repository.delete(resource);
    }
}
