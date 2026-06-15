package mx.unam.icf.aulas.modules.reservations.instances.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;
import mx.unam.icf.aulas.modules.academic.timeslots.infrastructure.TimeSlotRepository;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceResponseDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.mappers.ReservInstanceMapper;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlotId;
import mx.unam.icf.aulas.modules.reservations.slots.infrastructure.ReservSlotRepository;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service managing the full lifecycle of {@link ReservInstance} entities.
 *
 * <p>Covers reservation creation (with conflict detection and DFR business rules),
 * admin approval/rejection, user and admin cancellation, and classroom reassignment.</p>
 *
 * <p>Business rules enforced on creation (DFR §4):</p>
 * <ul>
 *   <li>Date must not be in the past and must not fall on a Sunday.</li>
 *   <li>When the reservation is for the current day, the earliest requested slot
 *       must start at least 15 minutes from now.</li>
 *   <li>No approved slot conflicts may exist for the same classroom on the same date.</li>
 * </ul>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class ReservInstanceService {

    private final ReservInstanceRepository repository;
    private final ReservInstanceMapper mapper;
    private final ReservationGroupRepository groupRepository;
    private final ClassroomRepository classroomRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ReservSlotRepository slotRepository;

    /**
     * Returns all reservation instances in the system.
     * GET /api/v1/reservations
     */
    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findAll() {
        return mapper.toDtoList(repository.findAll());
    }

    /**
     * Returns all reservation instances currently awaiting administrator review.
     * Restricted to ADMIN role.
     * GET /api/v1/reservations/pending
     */
    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findPending() {
        return mapper.toDtoList(repository.findByStatus(ReservInstanceStatus.PENDIENTE));
    }

    /**
     * Returns a single reservation instance by its public UUID.
     *
     * @param uuid public UUID of the instance
     * @throws ResourceNotFoundException when no instance matches the given UUID
     */
    @Transactional(readOnly = true)
    public ReservInstanceResponseDTO findByUuid(UUID uuid) {
        return mapper.toDto(
            repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation instance not found: " + uuid))
        );
    }

    /**
     * Returns all reservation instances belonging to a specific user.
     *
     * @param userUuid public UUID of the target user
     */
    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findByUser(UUID userUuid) {
        return mapper.toDtoList(repository.findByUserUuid(userUuid));
    }

    /**
     * Returns approved reservation instances for a classroom within a date range.
     * Used by the availability calendar.
     *
     * @param classroomUuid public UUID of the classroom
     * @param from          start of the date range (inclusive)
     * @param to            end of the date range (inclusive)
     */
    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findAvailability(UUID classroomUuid, LocalDate from, LocalDate to) {
        return mapper.toDtoList(
            repository.findApprovedByClassroomAndDateRange(classroomUuid, from, to, ReservInstanceStatus.APROBADA)
        );
    }

    /**
     * Creates a new reservation instance with status {@code PENDIENTE}.
     *
     * <p>Validates business rules before persisting: inactive classroom, past date,
     * Sunday restriction, 15-minute advance notice, and approved-slot conflict detection.
     * Time-slot bookings ({@link ReservSlot}) are persisted immediately after the instance.</p>
     *
     * <p>The {@code principalUuid} must match the owner of the reservation group to prevent
     * a Maestro from creating reservations on behalf of another user (BOLA/IDOR protection).</p>
     *
     * @param dto           creation payload including time slot IDs
     * @param principalUuid public UUID of the authenticated user making the request
     * @throws AccessDeniedException     when the group does not belong to the authenticated user
     * @throws ResourceNotFoundException when the group, classroom, or a time slot is not found
     * @throws DomainException           when any business rule is violated
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO save(ReservInstanceRequestDTO dto, UUID principalUuid) {
        ReservationGroup group = groupRepository.findByUuid(dto.groupUuid())
            .orElseThrow(() -> new ResourceNotFoundException("Reservation group not found: " + dto.groupUuid()));

        if (!group.getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only create reservations for your own reservation groups");

        Classroom classroom = classroomRepository.findByUuid(dto.classroomUuid())
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + dto.classroomUuid()));

        if (Boolean.FALSE.equals(classroom.getIsActive()))
            throw new DomainException("The requested classroom is inactive and cannot be reserved");

        if (dto.date().isBefore(LocalDate.now()))
            throw new DomainException("Reservation date cannot be in the past");

        if (dto.date().getDayOfWeek() == DayOfWeek.SUNDAY)
            throw new DomainException("Reservations cannot be made on Sundays");

        List<TimeSlot> timeSlots = dto.timeSlotIds().stream()
            .map(id -> timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found: " + id)))
            .toList();

        if (dto.date().equals(LocalDate.now())) {
            LocalTime cutoff = LocalTime.now().plusMinutes(15);
            TimeSlot earliest = timeSlots.stream()
                .min(Comparator.comparing(TimeSlot::getStartTime))
                .orElseThrow();
            if (earliest.getStartTime().isBefore(cutoff))
                throw new DomainException("Reservation must be made at least 15 minutes in advance");
        }

        if (repository.existsConflict(classroom.getId(), dto.date(), dto.timeSlotIds(), ReservInstanceStatus.APROBADA))
            throw new DomainException("The requested classroom already has an approved reservation for one or more of the selected time slots on " + dto.date());

        ReservInstance instance = mapper.toEntity(dto);
        instance.setGroup(group);
        instance.setClassroom(classroom);
        instance.setStatus(ReservInstanceStatus.PENDIENTE);
        ReservInstance saved = repository.save(instance);

        Long userId = group.getUser().getId();
        for (TimeSlot ts : timeSlots) {
            ReservSlot slot = new ReservSlot();
            slot.setId(new ReservSlotId(saved.getId(), ts.getId()));
            slot.setInstance(saved);
            slot.setTimeSlot(ts);
            slot.setClassroomId(classroom.getId());
            slot.setUserId(userId);
            slot.setDate(dto.date());
            slotRepository.save(slot);
        }

        return mapper.toDto(saved);
    }

    /**
     * Approves a pending reservation instance.
     * Restricted to ADMIN role.
     *
     * @param uuid public UUID of the instance
     * @throws DomainException when the instance is not in {@code PENDIENTE} status
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO approve(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (instance.getStatus() != ReservInstanceStatus.PENDIENTE)
            throw new DomainException("Only PENDIENTE reservations can be approved");
        instance.setStatus(ReservInstanceStatus.APROBADA);
        return mapper.toDto(repository.save(instance));
    }

    /**
     * Rejects a pending reservation instance.
     * Restricted to ADMIN role.
     *
     * @param uuid public UUID of the instance
     * @throws DomainException when the instance is not in {@code PENDIENTE} status
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO reject(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (instance.getStatus() != ReservInstanceStatus.PENDIENTE)
            throw new DomainException("Only PENDIENTE reservations can be rejected");
        instance.setStatus(ReservInstanceStatus.RECHAZADA);
        return mapper.toDto(repository.save(instance));
    }

    /**
     * Cancels a reservation instance as the owning teacher.
     *
     * <p>The {@code principalUuid} must match the owner of the reservation to prevent
     * a Maestro from cancelling another Maestro's reservation (DFR §4.2 / BOLA protection).</p>
     *
     * @param uuid          public UUID of the instance
     * @param principalUuid public UUID of the authenticated user performing the cancellation
     * @throws AccessDeniedException when the reservation does not belong to the authenticated user
     * @throws DomainException       when the reservation is already cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO cancelByUser(UUID uuid, UUID principalUuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (!instance.getGroup().getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only cancel your own reservations");
        if (isCancelled(instance))
            throw new DomainException("Reservation is already cancelled");
        instance.setStatus(ReservInstanceStatus.CANCELADA_POR_MAESTRO);
        return mapper.toDto(repository.save(instance));
    }

    /**
     * Cancels a reservation instance as an administrator.
     * Restricted to ADMIN role.
     *
     * @param uuid public UUID of the instance
     * @throws DomainException when the reservation is already cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO cancelByAdmin(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (isCancelled(instance))
            throw new DomainException("Reservation is already cancelled");
        instance.setStatus(ReservInstanceStatus.CANCELADA_POR_ADMIN);
        return mapper.toDto(repository.save(instance));
    }

    /**
     * Reassigns an approved reservation to a different classroom.
     * Restricted to ADMIN role.
     *
     * @param uuid             public UUID of the instance
     * @param newClassroomUuid public UUID of the destination classroom
     * @throws DomainException when the instance is not approved or the classroom is inactive
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO reassign(UUID uuid, UUID newClassroomUuid) {
        ReservInstance instance = getOrThrow(uuid);

        if (instance.getStatus() != ReservInstanceStatus.APROBADA)
            throw new DomainException("Only APROBADA reservations can be reassigned");

        Classroom newClassroom = classroomRepository.findByUuid(newClassroomUuid)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + newClassroomUuid));

        if (Boolean.FALSE.equals(newClassroom.getIsActive()))
            throw new DomainException("The target classroom is inactive");

        instance.setClassroom(newClassroom);
        return mapper.toDto(repository.save(instance));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ReservInstance getOrThrow(UUID uuid) {
        return repository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation instance not found: " + uuid));
    }

    private boolean isCancelled(ReservInstance instance) {
        return instance.getStatus() == ReservInstanceStatus.CANCELADA_POR_MAESTRO
            || instance.getStatus() == ReservInstanceStatus.CANCELADA_POR_ADMIN;
    }
}
