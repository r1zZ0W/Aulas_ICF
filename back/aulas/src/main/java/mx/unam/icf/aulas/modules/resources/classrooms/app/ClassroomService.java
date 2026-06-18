package mx.unam.icf.aulas.modules.resources.classrooms.app;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.mappers.ClassroomMapper;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;

import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing the lifecycle of {@link Classroom} entities.
 *
 * <p>Enforces business rules such as unique classroom names and linked room validation.
 * Soft-delete is not applied here; a full delete removes the record from the database.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomMapper classroomMapper;
    private final ClassroomRepository classroomRepository;

    /**
     * Returns a page of all classrooms (active and inactive).
     * Intended for ADMIN users who need visibility into inactive rooms.
     *
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ClassroomResponseDTO> findAll(Pageable pageable) {
        return PageMapper.toDto(classroomRepository.findAll(pageable), classroomMapper::toDtoList);
    }

    /**
     * Returns a page of active classrooms only.
     * Used by non-admin users (Maestro) who should not see inactive rooms.
     *
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ClassroomResponseDTO> findAllActive(Pageable pageable) {
        return PageMapper.toDto(classroomRepository.findByIsActiveTrue(pageable), classroomMapper::toDtoList);
    }

    /**
     * Returns a single classroom by its public UUID.
     *
     * @param uuid public UUID of the classroom
     * @throws ResourceNotFoundException when no classroom matches the given UUID
     */
    @Transactional(readOnly = true)
    public ClassroomResponseDTO findByUuid(UUID uuid) {
        return classroomMapper.toDto(
            classroomRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + uuid))
        );
    }

    /**
     * Creates a new classroom.
     *
     * @param dto creation payload
     * @throws DomainException           when a classroom with the same name already exists
     * @throws ResourceNotFoundException when the linked classroom UUID is provided but not found
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassroomResponseDTO save(ClassroomRequestDTO dto) {
        if (classroomRepository.findByName(dto.name()).isPresent())
            throw new DomainException("A classroom with that name already exists: " + dto.name());

        Classroom classroom = classroomMapper.toEntity(dto);

        if (dto.linkedRoomUuid() != null) {
            Classroom linked = classroomRepository.findByUuid(dto.linkedRoomUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Linked classroom not found: " + dto.linkedRoomUuid()));
            classroom.setLinkedRoom(linked);
        }

        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Updates an existing classroom.
     *
     * @param uuid public UUID of the classroom to update
     * @param dto  update payload
     * @throws DomainException           when the UUID is null or the new name is already taken
     * @throws ResourceNotFoundException when the classroom or linked classroom is not found
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassroomResponseDTO update(UUID uuid, ClassroomRequestDTO dto) {
        if (uuid == null)
            throw new DomainException("Classroom UUID is required to perform an update");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + uuid));

        if (!classroom.getName().equals(dto.name()) && classroomRepository.findByName(dto.name()).isPresent())
            throw new DomainException("A classroom with that name already exists: " + dto.name());

        classroomMapper.updateEntityFromDto(dto, classroom);

        if (dto.linkedRoomUuid() != null) {
            Classroom linked = classroomRepository.findByUuid(dto.linkedRoomUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Linked classroom not found: " + dto.linkedRoomUuid()));
            classroom.setLinkedRoom(linked);
        }

        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Deactivates a classroom (soft-delete) to preserve referential integrity with
     * existing reservations. The classroom is marked as inactive ({@code isActive=false})
     * and will no longer appear in the Maestro catalog or accept new reservations.
     *
     * <p>Physical deletion is intentionally avoided to comply with the DFR NFR
     * ("nada se elimina físicamente") and LFTAIP auditability requirements.</p>
     *
     * @param uuid public UUID of the classroom to deactivate
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when no classroom matches the given UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUuid(UUID uuid) {
        if (uuid == null)
            throw new DomainException("Classroom UUID is required to perform a deactivation");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + uuid));

        classroom.setIsActive(false);
        classroomRepository.save(classroom);
    }
}
