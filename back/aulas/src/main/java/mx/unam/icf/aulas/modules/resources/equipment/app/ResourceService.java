package mx.unam.icf.aulas.modules.resources.equipment.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.mappers.ResourceMapper;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import mx.unam.icf.aulas.modules.resources.equipment.infrastructure.ResourceRepository;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing the {@link Resource} equipment catalog.
 *
 * <p>Resources are pre-seeded catalog entries (e.g., PROYECTOR, COMPUTADORA).
 * Administrators may add new equipment types or update existing ones.</p>
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
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ResourceDTO> findAll(Pageable pageable) {
        return PageMapper.toDto(repository.findAll(pageable), mapper::toDtoList);
    }

    /**
     * Returns a single equipment resource by its internal ID.
     *
     * @param id internal auto-generated identifier
     * @throws ResourceNotFoundException when no resource matches the given ID
     */
    @Transactional(readOnly = true)
    public ResourceDTO findById(Integer id) {
        return mapper.toDto(
            repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + id))
        );
    }

    /**
     * Creates a new equipment resource in the catalog. Requires ADMIN role.
     *
     * @param dto creation payload
     * @throws DomainException when a resource with the same name already exists
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourceDTO save(ResourceDTO dto) {
        if (repository.findByName(dto.name()).isPresent())
            throw new DomainException("An equipment resource with that name already exists: " + dto.name());

        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    /**
     * Updates an existing equipment resource. Requires ADMIN role.
     *
     * @param id  internal identifier of the resource to update
     * @param dto update payload
     * @throws ResourceNotFoundException when no resource matches the given ID
     * @throws DomainException           when the new name is already taken by another resource
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourceDTO update(Integer id, ResourceDTO dto) {
        Resource resource = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + id));

        if (!resource.getName().equals(dto.name()) && repository.findByName(dto.name()).isPresent())
            throw new DomainException("An equipment resource with that name already exists: " + dto.name());

        mapper.updateEntityFromDto(dto, resource);
        return mapper.toDto(repository.save(resource));
    }

    /**
     * Deletes an equipment resource from the catalog. Requires ADMIN role.
     *
     * @param id internal identifier of the resource to delete
     * @throws ResourceNotFoundException when no resource matches the given ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Resource resource = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + id));
        repository.delete(resource);
    }
}
