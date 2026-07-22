package mx.unam.icf.aulas.modules.resources.allocations.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.resources.allocations.app.dtos.ClassroomResourceRequestDTO;
import mx.unam.icf.aulas.modules.resources.allocations.app.dtos.ClassroomResourceResponseDTO;
import mx.unam.icf.aulas.modules.resources.allocations.app.mappers.ClassroomResourceMapper;
import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResource;
import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResourceId;
import mx.unam.icf.aulas.modules.resources.allocations.infrastructure.ClassroomResourceRepository;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import mx.unam.icf.aulas.modules.resources.equipment.infrastructure.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing classroom–equipment allocations.
 *
 * <p>Allows administrators to assign equipment (resources) to classrooms with quantity tracking.
 * Each allocation is uniquely identified by the classroom + resource pair.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class ClassroomResourceService {

    private final ClassroomResourceRepository allocationRepository;
    private final ClassroomRepository classroomRepository;
    private final ResourceRepository resourceRepository;
    private final ClassroomResourceMapper mapper;

    /**
     * Returns all equipment allocations for a given classroom.
     * GET /api/v1/classrooms/{uuid}/resources
     *
     * @param classroomUuid public UUID of the classroom
     * @throws ResourceNotFoundException when the classroom does not exist
     */
    @Transactional(readOnly = true)
    public List<ClassroomResourceResponseDTO> findByClassroom(UUID classroomUuid) {
        Classroom classroom = classroomRepository.findByUuid(classroomUuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomUuid));
        return mapper.toDtoList(allocationRepository.findByClassroomId(classroom.getId()));
    }

    /**
     * Assigns or updates the quantity of a piece of equipment for a classroom.
     * POST /api/v1/classrooms/{uuid}/resources
     *
     * @param classroomUuid public UUID of the classroom
     * @param dto           allocation payload with resourceUuid and quantity
     * @throws ResourceNotFoundException when the classroom or equipment resource does not exist
     * @throws DomainException           when the quantity is not positive
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassroomResourceResponseDTO save(UUID classroomUuid, ClassroomResourceRequestDTO dto) {
        Classroom classroom = classroomRepository.findByUuid(classroomUuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomUuid));

        Resource resource = resourceRepository.findByUuid(dto.resourceUuid())
            .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + dto.resourceUuid()));

        if (dto.quantity() == null || dto.quantity() < 1)
            throw new DomainException("Quantity must be at least 1");

        ClassroomResourceId id = new ClassroomResourceId(classroom.getId(), resource.getId());
        ClassroomResource allocation = allocationRepository.findById(id)
            .orElse(new ClassroomResource(id, classroom, resource, dto.quantity()));

        allocation.setQuantity(dto.quantity());
        return mapper.toDto(allocationRepository.save(allocation));
    }

    /**
     * Removes an equipment allocation from a classroom.
     * DELETE /api/v1/classrooms/{classroomUuid}/resources/{resourceUuid}
     *
     * @param classroomUuid public UUID of the classroom
     * @param resourceUuid  public UUID of the equipment resource to remove
     * @throws ResourceNotFoundException when the classroom, resource, or allocation does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID classroomUuid, UUID resourceUuid) {
        Classroom classroom = classroomRepository.findByUuid(classroomUuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomUuid));

        Resource resource = resourceRepository.findByUuid(resourceUuid)
            .orElseThrow(() -> new ResourceNotFoundException("Equipment resource not found: " + resourceUuid));

        ClassroomResourceId id = new ClassroomResourceId(classroom.getId(), resource.getId());
        ClassroomResource allocation = allocationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Equipment allocation not found for classroom " + classroomUuid + " and resource " + resourceUuid));

        allocationRepository.delete(allocation);
    }
}
