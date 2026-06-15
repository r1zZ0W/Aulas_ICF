package mx.unam.icf.aulas.modules.academic.semesters.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.mappers.SemesterMapper;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing the academic {@link Semester} catalog.
 *
 * <p>Semesters define the academic periods during which classroom reservations are active.
 * Ideally only one semester should have {@code isActive = true} at a time.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository repository;
    private final SemesterMapper mapper;

    /**
     * Returns all semesters in the catalog.
     * GET /api/v1/semesters
     */
    @Transactional(readOnly = true)
    public List<SemesterDTO> findAll() {
        return mapper.toDtoList(repository.findAll());
    }

    /**
     * Returns all semesters currently marked as active.
     * GET /api/v1/semesters/active
     */
    @Transactional(readOnly = true)
    public List<SemesterDTO> findActive() {
        return mapper.toDtoList(repository.findByIsActiveTrue());
    }

    /**
     * Creates a new semester. Requires ADMIN role.
     *
     * <p>New semesters are automatically marked as active on creation.</p>
     *
     * @param dto creation payload
     * @throws DomainException when a semester with the same name already exists
     */
    @Transactional(rollbackFor = Exception.class)
    public SemesterDTO save(SemesterDTO dto) {
        if (repository.findByName(dto.name()).isPresent())
            throw new DomainException("A semester with that name already exists: " + dto.name());

        Semester semester = mapper.toEntity(dto);
        semester.setIsActive(true);
        return mapper.toDto(repository.save(semester));
    }

    /**
     * Updates an existing semester. Requires ADMIN role.
     *
     * @param id  internal identifier of the semester to update
     * @param dto update payload
     * @throws ResourceNotFoundException when no semester matches the given ID
     * @throws DomainException           when the new name is already taken by another semester
     */
    @Transactional(rollbackFor = Exception.class)
    public SemesterDTO update(Long id, SemesterDTO dto) {
        Semester semester = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + id));

        if (!semester.getName().equals(dto.name()) && repository.findByName(dto.name()).isPresent())
            throw new DomainException("A semester with that name already exists: " + dto.name());

        mapper.updateEntityFromDto(dto, semester);
        return mapper.toDto(repository.save(semester));
    }
}
