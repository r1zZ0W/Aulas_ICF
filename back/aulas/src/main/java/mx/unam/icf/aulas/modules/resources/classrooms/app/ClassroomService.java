package mx.unam.icf.aulas.modules.resources.classrooms.app;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.mappers.ClassroomMapper;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service class for managing classroom operations.
 *
 * This service provides business logic for CRUD (Create, Read, Update, Delete) operations
 * on classroom entities. It acts as an intermediary between the presentation layer (controller)
 * and the persistence layer (repository), handling data transformations via the mapper.
 *
 * @author Ithera
 * @version 1.0
 * @see Classroom
 * @see ClassroomResponseDTO
 */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    /**
     * Mapper instance for converting between Classroom dto and classroom DTOs.
     */
    private final ClassroomMapper classroomMapper;

    /**
     * Repository instance for classroom data access operations.
     */
    private final ClassroomRepository classroomRepository;

    /**
     * Retrieves all classrooms from the database and converts them to DTOs.
     *
     * @return a list of all classroom DTOs
     */
    @Transactional(readOnly = true)
    public List<ClassroomResponseDTO> findAll() {
        return classroomMapper.toDtoList(classroomRepository.findAll());
    }

    /**
     * Retrieves a specific classroom by its public UUID.
     *
     * @param uuid the public UUID of the classroom to retrieve
     * @return the classroom DTO associated with the provided UUID
     * @throws ResourceNotFoundException when no classroom exists with the provided UUID
     */
    @Transactional(readOnly = true)
    public ClassroomResponseDTO findByUuid(UUID uuid) {
        return classroomMapper.toDto(
            classroomRepository.findByUuid(uuid)
                        .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with uuid: " + uuid))
        );
    }

    /**
     * Creates and persists a new classroom in the database.
     *
     * @param dto the classroom DTO containing the data to persist
     * @return the persisted classroom DTO with its generated UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassroomResponseDTO save(ClassroomRequestDTO dto) {

        classroomRepository.findByName(dto.name()).orElseThrow(
                () -> new ResourceNotFoundException("There is another classroom with ts name lil bro: " + dto.name())
        );

        Classroom classroom = classroomMapper.toEntity(dto);

        if (dto.linkedRoomUuid() != null) {
            Classroom linked = classroomRepository.findByUuid(dto.linkedRoomUuid())
                    .orElseThrow(() -> new ResourceNotFoundException("Linked classroom not found with uuid: " + dto.linkedRoomUuid()));
            classroom.setLinkedRoom(linked);
        }

        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Updates an existing classroom with new data.
     *
     * @param dto the classroom DTO containing the updated data
     * @return the updated classroom DTO
     * @throws DomainException when the DTO does not contain a valid UUID
     * @throws ResourceNotFoundException when no classroom exists with the provided UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassroomResponseDTO update(UUID uuid, ClassroomRequestDTO dto) {
        if (uuid == null)
            throw new DomainException("Classroom uuid is required to update a record");

        Classroom classroom = classroomRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with uuid: " + uuid));

        classroomMapper.updateEntityFromDto(dto, classroom);

        if (dto.linkedRoomUuid() != null) {
            Classroom linked = classroomRepository.findByUuid(dto.linkedRoomUuid())
                    .orElseThrow(() -> new ResourceNotFoundException("Linked classroom not found with uuid: " + dto.linkedRoomUuid()));
            classroom.setLinkedRoom(linked);
        }

        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Deletes a classroom from the database by its public UUID.
     *
     * @param uuid the public UUID of the classroom to delete
     * @throws DomainException when the UUID is null
     * @throws ResourceNotFoundException when no classroom exists with the provided UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUuid(UUID uuid) {
        if (uuid == null)
            throw new DomainException("Classroom uuid is required to delete a record");

        Classroom classroom = classroomRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with uuid: " + uuid));

        classroomRepository.delete(classroom);
    }
}
