package mx.unam.icf.aulas.modules.reservations.instances.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.kernel.infrastructure.services.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;
import mx.unam.icf.aulas.modules.academic.timeslots.infrastructure.TimeSlotRepository;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReassignRequestDTO;
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
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing the full lifecycle of {@link ReservInstance} entities.
 *
 * <p>Business rules enforced on creation (DFR §4.1):</p>
 * <ul>
 *   <li>Classroom must be active.</li>
 *   <li>Date must not be in the past and must not fall on a Sunday.</li>
 *   <li>When the reservation is for the current day, the earliest requested slot
 *       must start at least 15 minutes from now.</li>
 *   <li>No approved slot conflicts may exist for the same classroom on the same date.</li>
 *   <li>The authenticated principal must own the reservation group (BOLA/IDOR protection).</li>
 * </ul>
 *
 * <p>Reassignment (DFR §4.3) supports changing the classroom, the time-slot block, or both.
 * Conflict re-check excludes the instance being reassigned to avoid false self-conflicts.</p>
 *
 * @author Ithera
 * @version 3.0
 */
@Service
@RequiredArgsConstructor
public class ReservInstanceService {

    private final ReservInstanceRepository repository;
    private final ReservInstanceMapper     mapper;
    private final ReservationGroupRepository groupRepository;
    private final ClassroomRepository      classroomRepository;
    private final TimeSlotRepository       timeSlotRepository;
    private final ReservSlotRepository     slotRepository;
    private final UserRepository           userRepository;
    private final NotificationService      notificationService;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findPending() {
        return mapper.toDtoList(repository.findByStatus(ReservInstanceStatus.PENDIENTE));
    }

    @Transactional(readOnly = true)
    public ReservInstanceResponseDTO findByUuid(UUID uuid) {
        return mapper.toDto(
            repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation instance not found: " + uuid))
        );
    }

    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findByUser(UUID userUuid) {
        return mapper.toDtoList(repository.findByUserUuid(userUuid));
    }

    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findAvailability(UUID classroomUuid, LocalDate from, LocalDate to) {
        return mapper.toDtoList(
            repository.findApprovedByClassroomAndDateRange(classroomUuid, from, to, ReservInstanceStatus.APROBADA)
        );
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    /**
     * Creates a new reservation instance with status {@code PENDIENTE}.
     *
     * <p>The {@code principalUuid} must match the owner of the reservation group to prevent
     * a Maestro from creating reservations on behalf of another user (BOLA/IDOR protection).</p>
     * <p>After persisting, notifies the Maestro and all active administrators by email (DFR §4.1).</p>
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

        // DFR §4.1: notify Maestro and all active admins (best-effort).
        List<String> adminEmails = userRepository.findByRole_NameAndIsActiveTrue("ADMIN")
            .stream().map(u -> u.getEmail()).collect(Collectors.toList());
        var owner = saved.getGroup().getUser();
        notificationService.notifyReservationCreated(
                owner.getEmail(),
                owner.getFirstName() + " " + owner.getLastNames(),
                saved.getClassroom().getName(),
                saved.getDate(),
                adminEmails
        );

        return mapper.toDto(saved);
    }

    // ── Admin status mutations ────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO approve(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (instance.getStatus() != ReservInstanceStatus.PENDIENTE)
            throw new DomainException("Only PENDIENTE reservations can be approved");
        instance.setStatus(ReservInstanceStatus.APROBADA);
        return mapper.toDto(repository.save(instance));
    }

    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO reject(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (instance.getStatus() != ReservInstanceStatus.PENDIENTE)
            throw new DomainException("Only PENDIENTE reservations can be rejected");
        instance.setStatus(ReservInstanceStatus.RECHAZADA);
        return mapper.toDto(repository.save(instance));
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    /**
     * Cancels a reservation instance as the owning teacher (DFR §4.2).
     *
     * @param uuid          public UUID of the instance
     * @param principalUuid public UUID of the authenticated user
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

    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO cancelByAdmin(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (isCancelled(instance))
            throw new DomainException("Reservation is already cancelled");
        instance.setStatus(ReservInstanceStatus.CANCELADA_POR_ADMIN);
        return mapper.toDto(repository.save(instance));
    }

    // ── Reassignment (DFR §4.3) ───────────────────────────────────────────────

    /**
     * Reassigns an approved reservation to a different classroom and/or a different set of time slots.
     * Restricted to ADMIN role.
     *
     * <p>At least one of {@code dto.newClassroomUuid()} or {@code dto.newTimeSlotIds()} must be non-null.
     * A conflict re-check (excluding the instance itself) is performed before applying changes.
     * When only the classroom changes, existing {@link ReservSlot} rows are updated in place.
     * When the time slots change, all slots are deleted and recreated.</p>
     * <p>The Maestro is notified by email after a successful reassignment (best-effort, DFR §4.3).</p>
     *
     * @param uuid public UUID of the instance to reassign
     * @param dto  reassignment request (classroom and/or time slots)
     * @throws DomainException           when the instance is not APROBADA, or neither field is supplied
     * @throws ResourceNotFoundException when the new classroom or a time slot is not found
     * @throws DomainException           when the target classroom is inactive or a conflict exists
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO reassign(UUID uuid, ReassignRequestDTO dto) {
        if (dto.newClassroomUuid() == null && (dto.newTimeSlotIds() == null || dto.newTimeSlotIds().isEmpty()))
            throw new DomainException("At least one of newClassroomUuid or newTimeSlotIds must be provided");

        ReservInstance instance = getOrThrow(uuid);

        if (instance.getStatus() != ReservInstanceStatus.APROBADA)
            throw new DomainException("Only APROBADA reservations can be reassigned");

        // Resolve destination classroom
        Classroom destClassroom;
        if (dto.newClassroomUuid() != null) {
            destClassroom = classroomRepository.findByUuid(dto.newClassroomUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + dto.newClassroomUuid()));
            if (Boolean.FALSE.equals(destClassroom.getIsActive()))
                throw new DomainException("The target classroom is inactive");
        } else {
            destClassroom = instance.getClassroom();
        }

        // Resolve destination time slots
        List<Integer> destTimeSlotIds;
        List<TimeSlot> destTimeSlots;
        boolean slotsChanging;

        if (dto.newTimeSlotIds() != null && !dto.newTimeSlotIds().isEmpty()) {
            destTimeSlotIds = dto.newTimeSlotIds();
            destTimeSlots = destTimeSlotIds.stream()
                .map(id -> timeSlotRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Time slot not found: " + id)))
                .toList();
            slotsChanging = true;
        } else {
            // Keep current slots
            List<ReservSlot> currentSlots = slotRepository.findByInstance(instance);
            destTimeSlotIds = currentSlots.stream()
                .map(s -> s.getTimeSlot().getId())
                .collect(Collectors.toList());
            destTimeSlots = currentSlots.stream()
                .map(ReservSlot::getTimeSlot)
                .collect(Collectors.toList());
            slotsChanging = false;
        }

        // Conflict re-check (excluding self to prevent false self-conflict)
        if (repository.existsConflictExcluding(
                destClassroom.getId(), instance.getDate(), destTimeSlotIds,
                ReservInstanceStatus.APROBADA, instance.getId())) {
            throw new DomainException("The target classroom already has an approved reservation for one or more of the selected time slots on " + instance.getDate());
        }

        String oldClassroomName = instance.getClassroom().getName();

        // Apply classroom change
        instance.setClassroom(destClassroom);

        if (slotsChanging) {
            // Delete existing slots and recreate with new time slot set
            slotRepository.deleteByInstance(instance);
            slotRepository.flush();
            Long userId = instance.getGroup().getUser().getId();
            for (TimeSlot ts : destTimeSlots) {
                ReservSlot slot = new ReservSlot();
                slot.setId(new ReservSlotId(instance.getId(), ts.getId()));
                slot.setInstance(instance);
                slot.setTimeSlot(ts);
                slot.setClassroomId(destClassroom.getId());
                slot.setUserId(userId);
                slot.setDate(instance.getDate());
                slotRepository.save(slot);
            }
        } else if (dto.newClassroomUuid() != null) {
            // Only classroom changed — update classroomId on existing slots
            List<ReservSlot> existingSlots = slotRepository.findByInstance(instance);
            for (ReservSlot slot : existingSlots) {
                slot.setClassroomId(destClassroom.getId());
                slotRepository.save(slot);
            }
        }

        ReservInstance saved = repository.save(instance);

        // DFR §4.3: notify Maestro (best-effort)
        var reassignedOwner = saved.getGroup().getUser();
        notificationService.notifyReservationReassigned(
                reassignedOwner.getEmail(),
                reassignedOwner.getFirstName() + " " + reassignedOwner.getLastNames(),
                saved.getDate(),
                oldClassroomName,
                saved.getClassroom().getName()
        );

        return mapper.toDto(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReservInstance getOrThrow(UUID uuid) {
        return repository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation instance not found: " + uuid));
    }

    private boolean isCancelled(ReservInstance instance) {
        return instance.getStatus() == ReservInstanceStatus.CANCELADA_POR_MAESTRO
            || instance.getStatus() == ReservInstanceStatus.CANCELADA_POR_ADMIN;
    }
}
