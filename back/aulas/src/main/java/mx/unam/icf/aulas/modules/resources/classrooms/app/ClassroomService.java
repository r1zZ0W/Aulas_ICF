package mx.unam.icf.aulas.modules.resources.classrooms.app;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomStatsDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.mappers.ClassroomMapper;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;

import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service managing the lifecycle of {@link Classroom} entities.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li>Classroom names are unique across the catalog.</li>
 *   <li>Cycle prevention: a {@code linkedRoomUuid} that would create a
 *       circular parent chain (A→B→A, or self-reference) is rejected with a
 *       {@link DomainException} before persisting.</li>
 *   <li>Orphan cleanup (option A): deactivating a parent classroom automatically
 *       sets {@code linkedRoom = null} on all direct children, keeping them
 *       operational while preventing stale FK references.</li>
 *   <li>Physical deletion is intentionally avoided (DFR NFR "nada se elimina
 *       físicamente"). Use {@link #deactivate} / {@link #reactivate} instead.</li>
 * </ul>
 *
 * @author Ithera
 * @version 3.0
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
     * <p>When {@code search} is provided and non-blank, a case-insensitive {@code LIKE}
     * filter is applied over {@code name} and {@code description}, and {@code totalElements}
     * reflects the filtered count. When {@code search} is {@code null} or blank the full
     * catalog is returned.</p>
     *
     * @param search   optional free-text filter (may be {@code null})
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ClassroomResponseDTO> findAll(String search, Pageable pageable) {
        var page = (search != null && !search.isBlank())
                ? classroomRepository.search(search.trim(), pageable)
                : classroomRepository.findAll(pageable);
        return PageMapper.toDto(page, classroomMapper::toDtoList);
    }

    /**
     * Returns a page of active classrooms only.
     * Used by non-admin users (Maestro) who should not see inactive rooms.
     *
     * <p>When {@code search} is provided and non-blank, a case-insensitive {@code LIKE}
     * filter is applied over {@code name} and {@code description}. When {@code search} is
     * {@code null} or blank the full active catalog is returned.</p>
     *
     * @param search   optional free-text filter (may be {@code null})
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ClassroomResponseDTO> findAllActive(String search, Pageable pageable) {
        var page = (search != null && !search.isBlank())
                ? classroomRepository.searchActive(search.trim(), pageable)
                : classroomRepository.findByIsActiveTrue(pageable);
        return PageMapper.toDto(page, classroomMapper::toDtoList);
    }

    /**
     * Returns aggregated classroom statistics for the admin dashboard.
     *
     * <p>Resolves total, available (active), and not-available (inactive/null) counts
     * in a single database round-trip without materializing the full classroom list.</p>
     *
     * @return a {@link ClassroomStatsDTO} with the current counts
     */
    @Transactional(readOnly = true)
    public ClassroomStatsDTO getStats() {
        return classroomRepository.fetchStats();
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
            assertNoCycle(classroom, linked);
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
            assertNoCycle(classroom, linked);
            classroom.setLinkedRoom(linked);
        }

        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Deactivates a classroom (soft-delete) to preserve referential integrity with
     * existing reservations. The classroom is marked as inactive ({@code isActive=false})
     * and will no longer appear in the Maestro catalog or accept new reservations.
     *
     * <p>Orphan cleanup (option A): all direct child classrooms that reference this
     * classroom as their parent have their {@code linkedRoom} set to {@code null}
     * before the parent is deactivated. This prevents stale FK references while
     * keeping child classrooms operational.</p>
     *
     * <p>Physical deletion is intentionally avoided to comply with the DFR NFR
     * ("nada se elimina físicamente") and LFTAIP auditability requirements.</p>
     *
     * @param uuid public UUID of the classroom to deactivate
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when no classroom matches the given UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deactivate(UUID uuid) {
        if (uuid == null)
            throw new DomainException("Classroom UUID is required to perform a deactivation");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + uuid));

        classroomRepository.unlinkChildren(classroom.getId());
        classroom.setIsActive(false);
        classroomRepository.save(classroom);
    }

    /**
     * Reactivates a previously deactivated classroom, making it visible again in
     * the Maestro catalog and eligible for new reservations.
     *
     * <p>Child classrooms that were unlinked during a prior deactivation are
     * <em>not</em> automatically re-linked; the administrator must set their parent
     * explicitly if the grouping needs to be restored.</p>
     *
     * @param uuid public UUID of the classroom to reactivate
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when no classroom matches the given UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public void reactivate(UUID uuid) {
        if (uuid == null)
            throw new DomainException("Classroom UUID is required to perform a reactivation");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + uuid));

        classroom.setIsActive(true);
        classroomRepository.save(classroom);
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Walks the {@code linkedRoom} chain starting at {@code linked} and throws a
     * {@link DomainException} if {@code target} is encountered at any level.
     *
     * <p>This prevents "Matrioshka" cycles (A→B→A, or A pointing to itself) that
     * would produce infinite loops when the tree is traversed. The walk is safe
     * because both callers are {@code @Transactional}, so the lazy
     * {@code linkedRoom} association is initialized within the open session.</p>
     *
     * @param target     the classroom being saved or updated
     * @param linked     the proposed parent classroom (never {@code null})
     * @throws DomainException when linking {@code target} to {@code linked} would
     *                         introduce a cycle in the parent chain
     */
    private void assertNoCycle(Classroom target, Classroom linked) {
        Classroom cursor = linked;
        while (cursor != null) {
            if (cursor.getId() != null && cursor.getId().equals(target.getId()))
                throw new DomainException(
                    "Linked room creates a cycle in the parent chain: " + target.getName());
            cursor = cursor.getLinkedRoom();
        }
    }
}
