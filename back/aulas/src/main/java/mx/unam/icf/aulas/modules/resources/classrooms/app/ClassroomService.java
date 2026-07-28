package mx.unam.icf.aulas.modules.resources.classrooms.app;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.infrastructure.ReservSlotRepository;
import mx.unam.icf.aulas.modules.resources.allocations.infrastructure.ClassroomResourceRepository;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *   <li>Toggling a classroom's active status does not affect its children — child
 *       classrooms retain their {@code linkedRoom} reference regardless of whether
 *       the parent is active or inactive.</li>
 * </ul>
 *
 * <p><strong>Architecture warning — hard cascade delete.</strong>
 * The {@link #delete} method performs a <em>cascading hard delete</em>: it permanently
 * removes all dependent {@code ReservSlot}, {@code ReservInstance}, {@code ClassroomResource}
 * and orphan {@code ReservationGroup} rows before deleting the classroom itself. This
 * <strong>breaks the DFR NFR "nada se elimina físicamente"</strong> and LFTAIP auditability
 * requirements (reservation history, equipment-allocation trail, and academic traceability
 * are all lost). The cleaner alternatives — making dependent FKs NULLABLE via a Flyway/
 * Liquibase migration with SET NULL, or implementing {@code @SQLDelete}/{@code @Where}
 * soft-delete across all dependent entities — are explicitly out of scope at this time.
 * This trade-off was accepted by the project owner and must be re-evaluated before
 * production deployment in a regulated environment.</p>
 *
 * <p>For non-destructive status changes, use {@link #toggleStatus}.</p>
 *
 * @author Ithera
 * @version 4.0
 */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomMapper classroomMapper;
    private final ClassroomRepository classroomRepository;
    private final ReservSlotRepository reservSlotRepository;
    private final ReservInstanceRepository reservInstanceRepository;
    private final ClassroomResourceRepository classroomResourceRepository;
    private final ReservationGroupRepository reservationGroupRepository;

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
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + uuid))
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
            throw new DomainException(ErrorCode.CLASSROOM_NAME_TAKEN, "A classroom with that name already exists: " + dto.name());

        Classroom classroom = classroomMapper.toEntity(dto);

        if (dto.linkedRoomUuid() != null) {
            Classroom linked = classroomRepository.findByUuid(dto.linkedRoomUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Linked classroom not found: " + dto.linkedRoomUuid()));
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
            throw new DomainException(ErrorCode.INVALID_PARAMETERS, "Classroom UUID is required to perform an update");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + uuid));

        if (!classroom.getName().equals(dto.name()) && classroomRepository.findByName(dto.name()).isPresent())
            throw new DomainException(ErrorCode.CLASSROOM_NAME_TAKEN, "A classroom with that name already exists: " + dto.name());

        classroomMapper.updateEntityFromDto(dto, classroom);

        if (dto.linkedRoomUuid() != null) {
            Classroom linked = classroomRepository.findByUuid(dto.linkedRoomUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Linked classroom not found: " + dto.linkedRoomUuid()));
            assertNoCycle(classroom, linked);
            classroom.setLinkedRoom(linked);
        }

        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Permanently deletes a classroom and all its dependent data in a single transaction.
     *
     * <p>Deletion order (child-before-parent to satisfy FK constraints):</p>
     * <ol>
     *   <li>Capture the IDs of every {@code ReservationGroup} that owns an instance in
     *       this classroom (needed to detect orphans after step 4).</li>
     *   <li>Unlink self-referencing children ({@code linkedRoom = null} for direct children).</li>
     *   <li>Delete all {@code ReservSlot} rows whose {@code classroomId} matches
     *       (denormalized FK, must go first).</li>
     *   <li>Delete all {@code ReservInstance} rows assigned to this classroom.</li>
     *   <li>Delete all {@code ClassroomResource} equipment-allocation rows for this classroom.</li>
     *   <li>Delete any {@code ReservationGroup} that now has zero instances (orphan groups).
     *       Deletion is per-entity so that Hibernate also removes the
     *       {@code reservation_group_days} {@code @ElementCollection} rows. Groups spanning
     *       other classrooms are not affected — they still have remaining instances.</li>
     *   <li>Delete the classroom row itself.</li>
     * </ol>
     *
     * <p><strong>Data loss warning.</strong> This operation is irreversible and destroys
     * reservation history, equipment-allocation records, and academic traceability for the
     * deleted classroom. See the class-level Javadoc for the architectural trade-off.</p>
     *
     * @param uuid public UUID of the classroom to delete
     * @throws DomainException           when {@code uuid} is {@code null}
     * @throws ResourceNotFoundException when no classroom matches the given UUID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID uuid) {
        if (uuid == null)
            throw new DomainException(ErrorCode.INVALID_PARAMETERS, "Classroom UUID is required to perform a deletion");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + uuid));
        Long id = classroom.getId();

        // 1. Snapshot affected groups before we delete their instances.
        List<Long> affectedGroupIds = reservInstanceRepository.findGroupIdsByClassroomId(id);

        // 2. Unlink direct children (self-referencing parent chain).
        classroomRepository.unlinkChildren(id);

        // 3. Slots first — references both instance_id and denormalized classroom_id.
        reservSlotRepository.deleteAllByClassroomId(id);

        // 4. Instances — references classroom_id (NOT NULL FK).
        reservInstanceRepository.deleteAllByClassroomId(id);

        // 5. Equipment allocations — references classroom_id.
        classroomResourceRepository.deleteAllByClassroomId(id);

        // 6. Orphan-group cleanup: remove groups whose every instance was in this classroom.
        //    Per-entity deleteAll lets Hibernate clean the reservation_group_days @ElementCollection.
        if (!affectedGroupIds.isEmpty()) {
            var orphans = reservationGroupRepository.findAllById(affectedGroupIds).stream()
                .filter(g -> !reservInstanceRepository.existsByGroup_Id(g.getId()))
                .toList();
            if (!orphans.isEmpty())
                reservationGroupRepository.deleteAll(orphans);
        }

        // 7. Classroom row.
        classroomRepository.deleteById(id);
    }

    /**
     * Toggles the active status of a classroom between active and inactive.
     *
     * <p>If {@code isActive} is currently {@code true}, it is set to {@code false}
     * (classroom is removed from the Maestro catalog and can no longer accept
     * new reservations). If it is {@code false} or {@code null}, it is set to
     * {@code true} (classroom becomes available again).</p>
     *
     * <p>Child classrooms that reference this classroom via {@code linkedRoom} are
     * <em>not</em> affected — the parent–child relationship is preserved regardless
     * of the parent's active state. Physical deletion is intentionally avoided to
     * comply with the DFR NFR ("nada se elimina físicamente") and LFTAIP
     * auditability requirements.</p>
     *
     * @param uuid public UUID of the classroom whose status should be toggled
     * @throws DomainException           when the UUID is null
     * @throws ResourceNotFoundException when no classroom matches the given UUID
     * @return the updated classroom as a {@link ClassroomResponseDTO}
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassroomResponseDTO toggleStatus(UUID uuid) {
        if (uuid == null)
            throw new DomainException(ErrorCode.INVALID_PARAMETERS, "Classroom UUID is required to toggle its status");

        Classroom classroom = classroomRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + uuid));

        classroom.setIsActive(!Boolean.TRUE.equals(classroom.getIsActive()));
        return classroomMapper.toDto(classroomRepository.save(classroom));
    }

    /**
     * Atomically assigns a new set of direct child classrooms to the given parent.
     *
     * <p>Semantics (all within one transaction):</p>
     * <ul>
     *   <li>Each classroom whose UUID is in {@code childUuids} has its {@code linkedRoom}
     *       set to the parent.</li>
     *   <li>Each classroom that is <em>currently</em> a direct child of the parent but
     *       whose UUID is <em>not</em> in {@code childUuids} has its {@code linkedRoom}
     *       set to {@code null} (unlinked).</li>
     *   <li>Duplicate UUIDs in {@code childUuids} are silently deduplicated.</li>
     *   <li>An empty {@code childUuids} list removes all current children.</li>
     * </ul>
     *
     * <p>Validations (all return {@link DomainException} → HTTP 400):</p>
     * <ul>
     *   <li>Every UUID in {@code childUuids} must correspond to an existing classroom.</li>
     *   <li>Each desired child must be active ({@code isActive = true}).</li>
     *   <li>No desired child may be an ancestor of the parent (cycle prevention). The
     *       ancestor set is precomputed once in O(depth) and each check is O(1), avoiding
     *       the N-query pattern of calling {@link #assertNoCycle} per child.</li>
     * </ul>
     *
     * @param parentUuid public UUID of the parent classroom
     * @param childUuids desired full set of direct child UUIDs (may be empty, never {@code null})
     * @throws ResourceNotFoundException when the parent classroom is not found
     * @throws DomainException           when a child UUID is invalid, inactive, or would form a cycle
     */
    @Transactional(rollbackFor = Exception.class)
    public void setChildren(UUID parentUuid, List<UUID> childUuids) {
        Classroom parent = classroomRepository.findByUuid(parentUuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + parentUuid));

        // Deduplicate the desired set silently.
        Set<UUID> distinct = new HashSet<>(childUuids);

        // Bulk-fetch desired children in one query (O(1) round-trips, not O(N)).
        List<Classroom> desired = classroomRepository.findAllByUuidIn(distinct);
        if (desired.size() != distinct.size())
            throw new DomainException(ErrorCode.CLASSROOM_CHILDREN_NOT_FOUND, "One or more child UUIDs do not exist or were not found");

        // Precompute ancestor-id set once; each per-child cycle check is then O(1).
        Set<Long> ancestorIds = collectAncestorIds(parent);

        // Validate each desired child in-memory (no additional DB round-trips).
        for (Classroom child : desired) {
            if (!Boolean.TRUE.equals(child.getIsActive()))
                throw new DomainException(ErrorCode.CLASSROOM_CHILD_INACTIVE,
                    "Child classroom is not active: " + child.getUuid());
            if (ancestorIds.contains(child.getId()))
                throw new DomainException(ErrorCode.CLASSROOM_CYCLE_DETECTED,
                    "Linking " + child.getName() + " to " + parent.getName()
                    + " would create a cycle in the parent chain");
            child.setLinkedRoom(parent);
        }

        // Unlink classrooms that are current children but absent from the desired set.
        List<Classroom> toSave = new ArrayList<>(desired);
        for (Classroom current : classroomRepository.findByLinkedRoom_Id(parent.getId())) {
            if (!distinct.contains(current.getUuid())) {
                current.setLinkedRoom(null);
                toSave.add(current);
            }
        }

        classroomRepository.saveAll(toSave);
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
                throw new DomainException(ErrorCode.CLASSROOM_CYCLE_DETECTED,
                    "Linked room creates a cycle in the parent chain: " + target.getName());
            cursor = cursor.getLinkedRoom();
        }
    }

    /**
     * Collects the internal IDs of {@code node} and all of its ancestors by walking the
     * {@code linkedRoom} chain. Used by {@link #setChildren} to enable O(1) cycle detection
     * per proposed child, instead of calling {@link #assertNoCycle} N times.
     *
     * <p>The walk is safe within an open transaction because the lazy {@code linkedRoom}
     * association is initialized by Hibernate on access.</p>
     *
     * @param node the classroom whose ancestor chain should be collected (inclusive)
     * @return set of internal PKs of {@code node} and all its ancestors
     */
    private Set<Long> collectAncestorIds(Classroom node) {
        Set<Long> ids = new HashSet<>();
        Classroom cursor = node;
        while (cursor != null && cursor.getId() != null) {
            ids.add(cursor.getId());
            cursor = cursor.getLinkedRoom();
        }
        return ids;
    }
}
