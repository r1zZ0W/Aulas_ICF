package mx.unam.icf.aulas.modules.reservations.instances.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import mx.unam.icf.aulas.kernel.domain.events.reservations.cancellations.ReservInstanceCancelledEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.reassigns.ReservInstanceReassignEventDTO;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.reservations.history.app.ReservationHistoryService;
import mx.unam.icf.aulas.modules.reservations.history.domain.ReservationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.academic.timeslots.app.mappers.TimeSlotMapper;
import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;
import mx.unam.icf.aulas.modules.academic.timeslots.infrastructure.TimeSlotRepository;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.BookingRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReassignRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceFilter;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceResponseDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException;
import mx.unam.icf.aulas.modules.reservations.instances.app.mappers.ReservInstanceMapper;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceSpecification;
import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlotId;
import mx.unam.icf.aulas.modules.reservations.slots.infrastructure.ReservSlotRepository;
import mx.unam.icf.aulas.modules.reservations.students.app.StudentListStorageProperties;
import mx.unam.icf.aulas.modules.reservations.students.app.StudentRosterValidator;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.StudentCountMismatchException;
import mx.unam.icf.aulas.modules.reservations.students.domain.Student;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing the full lifecycle of {@link ReservInstance} entities.
 *
 * <p>Business rules enforced on creation:</p>
 * <ul>
 *   <li>Classroom must be active ({@code isActive = true}).</li>
 *   <li>Date must not be in the past and must not fall on a Sunday.</li>
 *   <li>Date must fall within the semester's inclusive {@code [startDate, endDate]} range.</li>
 *   <li>Date's day-of-week must be among the group's scheduled {@code daysOfWeek}.</li>
 *   <li>When the reservation is for the current day, the earliest requested slot
 *       must start at least 15 minutes from now.</li>
 *   <li>No active slot conflicts may exist for the same classroom on the same date
 *       (backed by {@code uk_reserv_slots_classroom_time}).</li>
 *   <li>The authenticated user must not already hold a slot on the same date and
 *       time-slot combination (backed by {@code uk_reserv_slots_user_time}).</li>
 *   <li>The authenticated principal must own the reservation group (BOLA/IDOR protection).</li>
 * </ul>
 *
 * <p>Cancellation physically deletes the reservation's {@link ReservSlot} rows so the
 * classroom slots are immediately available for new bookings.</p>
 *
 * <p>Reassignment supports changing the classroom, the time-slot block, or both.
 * Conflict checks run before any slot mutation; the slot delete is explicitly flushed
 * before the re-insert to satisfy the UNIQUE constraints during the same transaction.</p>
 *
 * @author Ithera
 * @version 4.0
 */
@Service
@RequiredArgsConstructor
public class ReservInstanceService {

    private final ReservInstanceRepository   reservInstanceRepository;
    private final ReservInstanceMapper       mapper;
    private final ReservationGroupRepository groupRepository;
    private final ClassroomRepository        classroomRepository;
    private final TimeSlotRepository         timeSlotRepository;
    private final TimeSlotMapper             timeSlotMapper;
    private final ReservSlotRepository       slotRepository;
    private final UserRepository             userRepository;
    private final SemesterRepository         semesterRepository;
    private final ApplicationEventPublisher  eventPublisher;
    private final ReservationHistoryService  historyService;
    private final StudentRosterValidator     rosterValidator;
    private final FileStorageService         fileStorage;
    private final StudentListStorageProperties rosterStorageProperties;
    private final Clock                      clock;

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a filtered, paginated list of all reservation instances in the system.
     *
     * <p>All filter fields in {@code filter} are optional; a {@code null}/blank value
     * means "no restriction on that dimension". Multiple non-null filters are combined
     * with AND. Eager loading of {@code group}, {@code group.user}, and {@code classroom}
     * is handled inside {@link ReservInstanceSpecification}; {@code slots} are loaded
     * lazily in batches.</p>
     *
     * @param filter   optional filter criteria; must not be {@code null} (use an all-null
     *                 instance for "no filters")
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page with the filtered count
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ReservInstanceResponseDTO> findAll(ReservInstanceFilter filter, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return PageMapper.toDto(
                reservInstanceRepository.findAll(ReservInstanceSpecification.build(filter, today), pageable),
                list -> mapper.toDtoList(list, today));
    }

    /**
     * Returns a single reservation instance by its public UUID.
     *
     * @param uuid public UUID of the instance
     * @throws ResourceNotFoundException when the instance is not found
     */
    @Transactional(readOnly = true)
    public ReservInstanceResponseDTO findByUuid(UUID uuid) {
        return mapper.toDto(
            reservInstanceRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_NOT_FOUND, "Reservation instance not found: " + uuid)),
            LocalDate.now(clock)
        );
    }

    /**
     * Returns a filtered, paginated list of reservation instances belonging to a specific user.
     *
     * <p>The {@code userUuid} restriction is injected into the filter so that the same
     * {@link ReservInstanceSpecification} is reused for both the admin "Todas" view and
     * the user-scoped "Mis Reservas" view. All other filter dimensions are preserved.</p>
     *
     * @param userUuid public UUID of the target user
     * @param filter   optional filter criteria (userUuid will be overridden with the path variable)
     * @param pageable pagination and sort criteria
     * @return a {@link PagedResultDTO} containing the requested page with the filtered count
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ReservInstanceResponseDTO> findByUser(UUID userUuid, ReservInstanceFilter filter, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return PageMapper.toDto(
                reservInstanceRepository.findAll(ReservInstanceSpecification.build(filter.withUser(userUuid), today), pageable),
                list -> mapper.toDtoList(list, today));
    }

    /**
     * Returns all active reservation instances within a date range.
     *
     * <p>When {@code classroomUuid} is provided, results are filtered to that classroom only
     * (used when the calendar shows a single-room view). When {@code null}, all active
     * classrooms are returned so the calendar can display every room at once.</p>
     *
     * @param classroomUuid public UUID of the classroom, or {@code null} for all classrooms
     * @param from          start of the date range (inclusive)
     * @param to            end of the date range (inclusive)
     * @return list of active instances with time slots eagerly loaded (no N+1)
     */
    @Transactional(readOnly = true)
    public List<ReservInstanceResponseDTO> findAvailability(UUID classroomUuid, LocalDate from, LocalDate to) {
        List<ReservInstance> instances = (classroomUuid != null)
            ? reservInstanceRepository.findActiveByClassroomAndDateRange(classroomUuid, from, to, ReservInstanceStatus.ACTIVE)
            : reservInstanceRepository.findActiveByDateRange(from, to, ReservInstanceStatus.ACTIVE);
        return mapper.toDtoList(instances, LocalDate.now(clock));
    }

    /**
     * Returns the time slots that are currently available (not yet booked) for a given
     * classroom on a given date, computed as {@code full catalog − occupied slots}.
     *
     * <p>Ordering is by {@code startTime} (the semantic field), not by the catalog's
     * autoincrement {@code id}: the frontend relies on the returned order to walk
     * chronological contiguity when building the "end time" options, and sorting by
     * {@code id} would silently break if a slot were ever inserted out of chronological
     * order relative to its id.</p>
     *
     * <p>No "already past" filtering is applied here — that is a UI concern (the frontend
     * already enforces the "at least 15 minutes from now" rule for today's date). This
     * method purely reports which catalog slots have no {@link ReservSlot} row yet.</p>
     *
     * @param classroomUuid public UUID of the classroom
     * @param date          date to check availability for
     * @return available time slots ordered by {@code startTime} ascending
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @Transactional(readOnly = true)
    public List<TimeSlotDTO> findAvailableSlots(UUID classroomUuid, LocalDate date) {
        Classroom classroom = classroomRepository.findByUuid(classroomUuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + classroomUuid));

        // Resolve the conflict scope for this classroom (parent + direct children or child + parent)
        List<Long> scope = resolveConflictClassroomScope(classroom.getId()).stream().toList();

        Set<Integer> occupiedIds = new HashSet<>(
            slotRepository.findOccupiedTimeSlotIdsInScope(scope, date));

        return timeSlotRepository.findAll(Sort.by(Sort.Direction.ASC, "startTime")).stream()
            .filter(ts -> !occupiedIds.contains(ts.getId()))
            .map(timeSlotMapper::toDto)
            .toList();
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    /**
     * Atomically creates a {@link ReservationGroup} and all its {@link ReservInstance} +
     * {@link ReservSlot} rows in a single database transaction, with the mandatory student
     * roster validated up-front and stored as the transaction's last step.
     *
     * <p>The frontend sends the booking <em>intent</em> once (classroom, time block,
     * optional recurrence pattern) plus the roster {@code .xlsx} in the same multipart
     * request; the backend generates every date occurrence. No client-side loops; no
     * partial saves on error.</p>
     *
     * <p>Business rules (enforced in order):</p>
      * <ol>
      *   <li><b>Roster first, before any database write</b>: the file must be a valid
      *       {@code .xlsx} with no duplicate students, and its student count must equal
      *       {@code attendeeCount} exactly — otherwise the reservation is never created.</li>
      *   <li>Classroom must be active.</li>
      *   <li>The requested {@code startDate} must not be after the currently active semester's end date.
      *       Requests beyond the active semester are rejected (400) immediately.</li>
      *   <li>Every target date must not be in the past, not be a Sunday, and fall within the
      *       semester window.</li>
      *   <li>Two bulk conflict queries (total 2 SELECTs regardless of the number of dates):
      *       one for classroom double-booking, one for user schedule conflicts. The first
      *       conflict found is returned as a structured 409 payload.</li>
      * </ol>
     *
     * <h4>Storage/notification ordering</h4>
     * <p>The roster file is written as the <em>last</em> statement of the transactional
     * method: a storage failure rolls the whole booking back, so no group ever exists
     * without a readable roster. The inverse residue (file written, then the database
     * commit itself fails) cannot be prevented by ordering — the filesystem is not
     * transactional — and is cleaned by {@code StudentRosterCleanupJob.reapOrphanFiles}.
     * The creation notification is published via {@code ReservInstanceCreatedEventDTO};
     * its listener ({@code kernel/app/listeners/ReservInstanceCreatedEvent}) is
     * {@code @TransactionalEventListener(AFTER_COMMIT)}, so the emails fire only once the
     * commit is consolidated — never for a rolled-back booking.</p>
     *
     * @param dto           atomic booking request (the {@code data} multipart part)
     * @param rosterBytes   raw bytes of the roster {@code .xlsx} (the {@code file} part)
     * @param principalUuid public UUID of the authenticated user making the request
     * @return list of created instances (one per target date), each with time slots populated
     * @throws ResourceNotFoundException       when the classroom or user is not found
     * @throws DomainException                 when a business rule is violated (400)
     * @throws StudentCountMismatchException   when roster size != attendeeCount (→ 422)
     * @throws ReservationConflictException    when a slot is already taken (→ 409)
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ReservInstanceResponseDTO> createBooking(BookingRequestDTO dto, byte[] rosterBytes, UUID principalUuid) {

        // 0. Validate the roster BEFORE touching the database: invalid file, duplicates,
        //    or a count mismatch must abort with nothing persisted anywhere.
        List<Student> students = rosterValidator.validate(rosterBytes);
        if (students.size() != dto.attendeeCount())
            throw new StudentCountMismatchException(dto.attendeeCount(), students.size());

        // 1. Resolve classroom
        Classroom classroom = classroomRepository.findByUuid(dto.classroomUuid())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + dto.classroomUuid()));
        if (!Boolean.TRUE.equals(classroom.getIsActive()))
            throw new DomainException(ErrorCode.CLASSROOM_INACTIVE, "The requested classroom is inactive and cannot be reserved");

        // 2.1 Resolve user by its UUID
        UUID target = dto.userUuid() != null ? dto.userUuid() : principalUuid;

        // 2.2 Fetch it into the database
        User user = userRepository.findByUuid(target)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with UUID: " + target));

        // 3. Ensure the requested start date does not exceed the currently active semester
        Semester currentSemester = semesterRepository.findCurrent(LocalDate.now(clock))
            .orElseThrow(() -> new DomainException(ErrorCode.SEMESTER_NO_ACTIVE, "No active semester available at this time"));

        if (dto.startDate().isAfter(currentSemester.getEndDate()))
            throw new DomainException(ErrorCode.RESERVATION_DATE_OUT_OF_SEMESTER, "Requested start date " + dto.startDate() + " is after the active semester end date " + currentSemester.getEndDate());

        // 4. Resolve target weekdays
        Set<DayOfWeek> days = (dto.daysOfWeek() == null || dto.daysOfWeek().isEmpty())
            ? Set.of(dto.startDate().getDayOfWeek())
            : new HashSet<>(dto.daysOfWeek());

        // 6. Create and persist the group (owns all generated instances). Groups are
        // created ACTIVE: the roster was already validated in step 0 and its file is
        // stored at the end of this same transaction.
        ReservationGroup group = new ReservationGroup();
        group.setUser(user);
        group.setSemester(currentSemester);
        group.setDaysOfWeek(days);
        group.setStatus(ReservationGroupStatus.ACTIVE);
        group = groupRepository.save(group);

        // 7. Build target dates in memory — only the startDate is considered if daysOfWeek is not specified
        LocalDate endDate = currentSemester.getEndDate();
        List<LocalDate> targetDates = new ArrayList<>();

        if (dto.daysOfWeek() == null || dto.daysOfWeek().isEmpty()) {
            targetDates.add(dto.startDate());
        } else {
            for (LocalDate d = dto.startDate(); !d.isAfter(endDate); d = d.plusDays(1))
                if (days.contains(d.getDayOfWeek()))
                    targetDates.add(d);


        }


        if (targetDates.isEmpty())
            throw new DomainException(ErrorCode.RESERVATION_NO_VALID_DATES,
                "No valid dates found between " + dto.startDate() + " and " + endDate +
                " for the specified weekdays");

        // 8. Load time slots (validate IDs exist)
        List<TimeSlot> timeSlots = timeSlotRepository.findAllById(dto.timeSlotIds());
        if (timeSlots.size() != dto.timeSlotIds().size())
            throw new ResourceNotFoundException(ErrorCode.TIMESLOT_NOT_FOUND, "One or more time slots were not found");


        // 9. Per-date in-memory validations (no DB round-trips here)
        LocalDate today    = LocalDate.now(clock);
        LocalTime nowTime  = LocalTime.now(clock);
        for (LocalDate d : targetDates) {
            if (d.isBefore(today))
                throw new DomainException(ErrorCode.RESERVATION_DATE_IN_PAST, "Date " + d + " is in the past");

            if (d.getDayOfWeek() == DayOfWeek.SUNDAY)
                throw new DomainException(ErrorCode.RESERVATION_ON_SUNDAY, "Reservations cannot be made on Sundays (date: " + d + ")");

            if (d.isBefore(currentSemester.getStartDate()) || d.isAfter(currentSemester.getEndDate()))
                throw new DomainException(ErrorCode.RESERVATION_DATE_OUT_OF_SEMESTER,
                    "Date " + d + " falls outside the semester period " +
                    currentSemester.getStartDate() + " – " + currentSemester.getEndDate());

            if (d.equals(today)) {
                LocalTime cutoff = nowTime.plusMinutes(15);
                TimeSlot earliest = timeSlots.stream()
                    .min(Comparator.comparing(TimeSlot::getStartTime))
                    .orElseThrow();

                if (earliest.getStartTime().isBefore(cutoff))
                    throw new DomainException(ErrorCode.RESERVATION_TOO_SOON,
                        "Reservation must be made at least 15 minutes in advance");

            }

        }

        // 10. Bulk conflict detection — exactly 2 SELECTs regardless of how many dates
        // Resolve the classroom scope (parent + direct children OR child + parent)
        List<Long> bookingScope = resolveConflictClassroomScope(classroom.getId()).stream().toList();
        List<ReservSlot> classroomConflicts = slotRepository.findClassroomConflictsInScope(
            bookingScope, dto.timeSlotIds(), targetDates);
        if (!classroomConflicts.isEmpty()) {
            ReservSlot first = classroomConflicts.getFirst();
            throw new ReservationConflictException(first.getDate(), first.getTimeSlot().getId());
        }

        List<ReservSlot> userConflicts = slotRepository.findUserConflicts(
            user.getId(), dto.timeSlotIds(), targetDates);
        if (!userConflicts.isEmpty()) {
            ReservSlot first = userConflicts.getFirst();
            throw new ReservationConflictException(first.getDate(), first.getTimeSlot().getId());
        }

        // 11. Batch-insert all instances
        List<ReservInstance> toSave = new ArrayList<>();
        for (LocalDate d : targetDates) {
            ReservInstance inst = new ReservInstance();
            inst.setGroup(group);
            inst.setClassroom(classroom);
            inst.setStatus(ReservInstanceStatus.ACTIVE);
            inst.setAttendeeCount(dto.attendeeCount());
            inst.setDate(d);
            inst.setTitle(dto.title());
            toSave.add(inst);
        }
        List<ReservInstance> saved = reservInstanceRepository.saveAll(toSave);

        // 12. Batch-insert all slots (all-or-nothing via @Transactional)
        List<ReservSlot> allSlots = new ArrayList<>();
        for (ReservInstance inst : saved) {
            for (TimeSlot ts : timeSlots) {
                ReservSlot slot = new ReservSlot();
                slot.setId(new ReservSlotId(inst.getId(), ts.getId()));
                slot.setInstance(inst);
                slot.setTimeSlot(ts);
                slot.setClassroomId(classroom.getId());
                slot.setUserId(user.getId());
                slot.setDate(inst.getDate());
                allSlots.add(slot);
            }
        }
        slotRepository.saveAll(allSlots);

        // 13. Record history for every created instance in a single batch
        historyService.registerAll(saved, ReservationEvent.CREATED, "Reservation created");

        // 14. Notify owner and admins. The event is only *registered* here — its listener
        // is @TransactionalEventListener(AFTER_COMMIT), so no email fires unless the commit
        // below actually consolidates. (The old flow deferred this to ReservationStudentService
        // .upload; the roster now arrives with the booking, so the notification does too.)
        ReservInstance first = saved.getFirst();
        List<String> adminEmails = userRepository.findByRoleName("ADMIN")
            .stream().map(User::getEmail).collect(Collectors.toList());
        eventPublisher.publishEvent(new ReservInstanceCreatedEventDTO(
            user.getEmail(),
            user.getFullName(),
            first.getClassroom().getName(),
            first.getDate(),
            slotStartTime(timeSlots),
            slotEndTime(timeSlots),
            group.getUuid(),
            adminEmails
        ));

        // 15. Store the roster file as the transaction's LAST statement: if this write
        // throws (FileStorageException), everything above rolls back and the reservation
        // never existed. The inverse case (write OK, commit fails afterwards) leaves an
        // orphan file that StudentRosterCleanupJob.reapOrphanFiles removes.
        fileStorage.store(rosterStorageProperties.getStorageDir(),
            group.getUuid() + ".xlsx", rosterBytes);

        return mapper.toDtoList(saved, today);
    }

    /**
     * Creates a new reservation instance with status {@link ReservInstanceStatus#ACTIVE}.
     *
     * <p>The instance occupies the classroom immediately — no admin approval is needed.
     * The {@code principalUuid} must match the owner of the reservation group to prevent
     * a Maestro from creating reservations on behalf of another user (BOLA/IDOR protection).</p>
     * <p>After persisting, notifies the Maestro and all active administrators by email.</p>
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
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_GROUP_NOT_FOUND, "Reservation group not found: " + dto.groupUuid()));

        if (!group.getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only create reservations for your own reservation groups");

        Classroom classroom = classroomRepository.findByUuid(dto.classroomUuid())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + dto.classroomUuid()));

        // Classroom must be explicitly active (null treated as inactive)
        if (!Boolean.TRUE.equals(classroom.getIsActive()))
            throw new DomainException(ErrorCode.CLASSROOM_INACTIVE, "The requested classroom is inactive and cannot be reserved");

        if (dto.date().isBefore(LocalDate.now(clock)))
            throw new DomainException(ErrorCode.RESERVATION_DATE_IN_PAST, "Reservation date cannot be in the past");

        if (dto.date().getDayOfWeek() == DayOfWeek.SUNDAY)
            throw new DomainException(ErrorCode.RESERVATION_ON_SUNDAY, "Reservations cannot be made on Sundays");

        // Date must fall within the semester's active window
        var semester = group.getSemester();
        if (dto.date().isBefore(semester.getStartDate()) || dto.date().isAfter(semester.getEndDate()))
            throw new DomainException(ErrorCode.RESERVATION_DATE_OUT_OF_SEMESTER,
                "Reservation date " + dto.date() + " falls outside the semester period " +
                semester.getStartDate() + " – " + semester.getEndDate());

        // Date's day-of-week must match the group's scheduled pattern
        if (!group.getDaysOfWeek().contains(dto.date().getDayOfWeek()))
            throw new DomainException(ErrorCode.RESERVATION_GROUP_SCHEDULE_MISMATCH,
                "The group is not scheduled on " + dto.date().getDayOfWeek().name().toLowerCase() +
                "s. Scheduled days: " + group.getDaysOfWeek());

        List<TimeSlot> timeSlots = dto.timeSlotIds().stream()
            .map(id -> timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TIMESLOT_NOT_FOUND, "Time slot not found: " + id)))
            .toList();

        if (dto.date().equals(LocalDate.now(clock))) {
            LocalTime cutoff = LocalTime.now(clock).plusMinutes(15);
            TimeSlot earliest = timeSlots.stream()
                .min(Comparator.comparing(TimeSlot::getStartTime))
                .orElseThrow();
            if (earliest.getStartTime().isBefore(cutoff))
                throw new DomainException(ErrorCode.RESERVATION_TOO_SOON, "Reservation must be made at least 15 minutes in advance");
        }

        // Classroom double-booking check (backed by uk_reserv_slots_classroom_time)
        List<Long> scopeForSave = resolveConflictClassroomScope(classroom.getId()).stream().toList();
        if (reservInstanceRepository.existsConflictInScope(scopeForSave, dto.date(), dto.timeSlotIds()))
             throw new DomainException(ErrorCode.RESERVATION_SLOT_CONFLICT,
                 "The requested classroom already has a reservation for one or more of the selected time slots on " + dto.date());

        // User self-conflict check (backed by uk_reserv_slots_user_time)
        Long userId = group.getUser().getId();
        if (reservInstanceRepository.existsUserConflict(userId, dto.date(), dto.timeSlotIds()))
            throw new DomainException(ErrorCode.RESERVATION_OWN_CONFLICT,
                "You already have a reservation for one or more of the selected time slots on " + dto.date());

        ReservInstance instance = mapper.toEntity(dto);
        instance.setGroup(group);
        instance.setClassroom(classroom);
        instance.setStatus(ReservInstanceStatus.ACTIVE);
        ReservInstance saved = reservInstanceRepository.save(instance);

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

        // Record history for this single instance
        historyService.register(saved, ReservationEvent.CREATED, "Reservation created");

        // Notify Maestro and all active admins (best-effort, via event)
        List<String> adminEmails = userRepository.findByRoleName("ADMIN")
            .stream().map(User::getEmail).collect(Collectors.toList());
        var owner = saved.getGroup().getUser();
        eventPublisher.publishEvent(new ReservInstanceCreatedEventDTO(
            owner.getEmail(),
            owner.getFullName(),
            saved.getClassroom().getName(),
            saved.getDate(),
            slotStartTime(timeSlots),
            slotEndTime(timeSlots),
            saved.getUuid(),
            adminEmails
        ));

        return mapper.toDto(saved, LocalDate.now(clock));
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    /**
     * Cancels a reservation instance as the owning teacher.
     *
     * <p>Sets the status to {@link ReservInstanceStatus#CANCELLED_BY_USER} and
     * physically removes all associated {@link ReservSlot} rows so the classroom
     * time slots become immediately available for new bookings.</p>
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
            throw new DomainException(ErrorCode.RESERVATION_ALREADY_CANCELLED, "Reservation is already cancelled");

        // Capture data BEFORE deleting slots (defensive — slots will be gone after deleteByInstance)
        String  maestroEmail    = instance.getGroup().getUser().getEmail();
        String  maestroFullName = instance.getGroup().getUser().getFullName();
        String  classroomName   = instance.getClassroom().getName();
        LocalDate date          = instance.getDate();
        UUID    reservationId   = instance.getUuid();
        LocalTime start         = slotStartTimeFromInstance(instance);
        LocalTime end           = slotEndTimeFromInstance(instance);
        List<String> adminEmails = userRepository.findByRoleName("ADMIN")
            .stream().map(User::getEmail).collect(Collectors.toList());

        instance.setStatus(ReservInstanceStatus.CANCELLED_BY_USER);
        slotRepository.deleteByInstance(instance);
        ReservInstance saved = reservInstanceRepository.save(instance);
        historyService.register(saved, ReservationEvent.CANCELLED_BY_USER, "Cancelled by owner");

        eventPublisher.publishEvent(new ReservInstanceCancelledEventDTO(
            maestroEmail, maestroFullName, classroomName, date,
            start, end, reservationId, false, null, adminEmails
        ));
        return mapper.toDto(saved, LocalDate.now(clock));
    }

    /**
     * Cancels a reservation instance as an administrator. Requires ADMIN role.
     *
     * <p>Sets the status to {@link ReservInstanceStatus#CANCELLED_BY_ADMIN} and
     * physically removes all associated {@link ReservSlot} rows so the classroom
     * time slots become immediately available for new bookings.</p>
     *
     * @param uuid public UUID of the instance to cancel
     * @throws DomainException when the reservation is already cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO cancelByAdmin(UUID uuid) {
        ReservInstance instance = getOrThrow(uuid);
        if (isCancelled(instance))
            throw new DomainException(ErrorCode.RESERVATION_ALREADY_CANCELLED, "Reservation is already cancelled");

        // Capture data BEFORE deleting slots (defensive — slots will be gone after deleteByInstance)
        String  maestroEmail    = instance.getGroup().getUser().getEmail();
        String  maestroFullName = instance.getGroup().getUser().getFullName();
        String  classroomName   = instance.getClassroom().getName();
        LocalDate date          = instance.getDate();
        UUID    reservationId   = instance.getUuid();
        LocalTime start         = slotStartTimeFromInstance(instance);
        LocalTime end           = slotEndTimeFromInstance(instance);
        List<String> adminEmails = userRepository.findByRoleName("ADMIN")
            .stream().map(User::getEmail).collect(Collectors.toList());

        instance.setStatus(ReservInstanceStatus.CANCELLED_BY_ADMIN);
        slotRepository.deleteByInstance(instance);
        ReservInstance saved = reservInstanceRepository.save(instance);
        historyService.register(saved, ReservationEvent.CANCELLED_BY_ADMIN, "Cancelled by administrator");

        eventPublisher.publishEvent(new ReservInstanceCancelledEventDTO(
            maestroEmail, maestroFullName, classroomName, date,
            start, end, reservationId, true, null, adminEmails
        ));
        return mapper.toDto(saved, LocalDate.now(clock));
    }

    // ── Reassignment ──────────────────────────────────────────────────────────

    /**
     * Reassigns an active reservation to a different classroom and/or a different set of time slots.
     * Restricted to ADMIN role.
     *
     * <p>At least one of {@code dto.newClassroomUuid()} or {@code dto.newTimeSlotIds()} must be
     * non-null. Both conflict checks ({@link ReservInstanceRepository#existsConflictInScope} and
     * {@link ReservInstanceRepository#existsUserConflictExcluding}) run <em>before</em> any slot
     * mutation so a controlled {@link DomainException} fires instead of a raw constraint violation.
     * When slots are replaced, the deletion is explicitly flushed before the re-insert to satisfy
     * the UNIQUE constraints within the same transaction.</p>
     * <p>The Maestro is notified by email after a successful reassignment (best-effort).</p>
     *
     * @param uuid public UUID of the instance to reassign
     * @param dto  reassignment request (classroom and/or time slots)
     * @throws DomainException           when the instance is not active, or neither field is supplied
     * @throws ResourceNotFoundException when the new classroom or a time slot is not found
     * @throws DomainException           when the target classroom is inactive or a conflict exists
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservInstanceResponseDTO reassign(UUID uuid, ReassignRequestDTO dto) {
        if (dto.newClassroomUuid() == null && (dto.newTimeSlotIds() == null || dto.newTimeSlotIds().isEmpty()))
            throw new DomainException(ErrorCode.REASSIGN_TARGET_REQUIRED, "At least one of newClassroomUuid or newTimeSlotIds must be provided");

        ReservInstance instance = getOrThrow(uuid);

        if (instance.getStatus() != ReservInstanceStatus.ACTIVE)
            throw new DomainException(ErrorCode.RESERVATION_NOT_ACTIVE, "Only active reservations can be reassigned");

        // Resolve destination classroom
        Classroom destClassroom;
        if (dto.newClassroomUuid() != null) {
            destClassroom = classroomRepository.findByUuid(dto.newClassroomUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLASSROOM_NOT_FOUND, "Classroom not found: " + dto.newClassroomUuid()));
            if (!Boolean.TRUE.equals(destClassroom.getIsActive()))
                throw new DomainException(ErrorCode.REASSIGN_CLASSROOM_INACTIVE, "The target classroom is inactive");
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
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TIMESLOT_NOT_FOUND, "Time slot not found: " + id)))
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

        // 1. Classroom conflict re-check (self-excluding) — must run before any mutation
        List<Long> destScope = resolveConflictClassroomScope(destClassroom.getId()).stream().toList();
        if (reservInstanceRepository.existsConflictExcludingInScope(
                destScope, instance.getDate(), destTimeSlotIds, instance.getId())) {
            throw new DomainException(ErrorCode.RESERVATION_SLOT_CONFLICT,
                "The target classroom already has a reservation for one or more of the selected time slots on " + instance.getDate());
        }

        // 2. User self-conflict re-check (self-excluding) — must run before any mutation
        Long userId = instance.getGroup().getUser().getId();
        if (reservInstanceRepository.existsUserConflictExcluding(userId, instance.getDate(), destTimeSlotIds, instance.getId())) {
            throw new DomainException(ErrorCode.RESERVATION_OWN_CONFLICT,
                "The teacher already has a reservation for one or more of the selected time slots on " + instance.getDate());
        }

        String oldClassroomName = instance.getClassroom().getName();

        // Apply classroom change
        instance.setClassroom(destClassroom);

        if (slotsChanging) {
            // 3. Delete existing slots, flush immediately so the DB sees the DELETEs before the INSERTs
            slotRepository.deleteByInstance(instance);
            slotRepository.flush();

            // 4. Recreate with the new time-slot set
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

        // Permanently mark this instance as reassigned so the frontend can surface
        // the contextual "Reasignada" badge without querying the audit log.
        // The instance status stays ACTIVE — reassignment is a display hint only.
        instance.setReassigned(true);

        ReservInstance saved = reservInstanceRepository.save(instance);

        // Record history
        historyService.register(saved, ReservationEvent.REASSIGNED, "Reassigned by administrator");

        // Notify Maestro and admins (best-effort, via event)
        var reassignedOwner = saved.getGroup().getUser();
        List<String> adminEmails = userRepository.findByRoleName("ADMIN")
            .stream().map(User::getEmail).collect(Collectors.toList());

        eventPublisher.publishEvent(new ReservInstanceReassignEventDTO(
            reassignedOwner.getEmail(),
            reassignedOwner.getFullName(),
            saved.getDate(),
            oldClassroomName,
            saved.getClassroom().getName(),
            slotStartTime(destTimeSlots),
            slotEndTime(destTimeSlots),
            saved.getUuid(),
            adminEmails
        ));

        return mapper.toDto(saved, LocalDate.now(clock));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReservInstance getOrThrow(UUID uuid) {
        return reservInstanceRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_NOT_FOUND, "Reservation instance not found: " + uuid));
    }

    private boolean isCancelled(ReservInstance instance) {
        return instance.getStatus() == ReservInstanceStatus.CANCELLED_BY_USER
            || instance.getStatus() == ReservInstanceStatus.CANCELLED_BY_ADMIN;
    }

    /**
     * Returns the start time of the earliest slot in a resolved {@link TimeSlot} list,
     * or {@code null} when the list is empty.
     */
    private LocalTime slotStartTime(List<TimeSlot> slots) {
        return slots.stream()
            .map(TimeSlot::getStartTime)
            .min(Comparator.naturalOrder())
            .orElse(null);
    }

    /**
     * Returns the end time of the latest slot in a resolved {@link TimeSlot} list,
     * or {@code null} when the list is empty.
     */
    private LocalTime slotEndTime(List<TimeSlot> slots) {
        return slots.stream()
            .map(TimeSlot::getEndTime)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    /**
     * Null-safe helper for cancellation: loads the slots for an instance via the
     * repository (avoids relying on the lazy collection state) and returns the
     * start time of the earliest slot. Returns {@code null} when no slots exist (anomaly).
     *
     * <p>Must be called <em>before</em> {@code slotRepository.deleteByInstance()} to
     * ensure the slot rows are still present in the database.</p>
     */
    private LocalTime slotStartTimeFromInstance(ReservInstance instance) {
        return slotRepository.findByInstance(instance).stream()
            .map(s -> s.getTimeSlot().getStartTime())
            .min(Comparator.naturalOrder())
            .orElse(null);
    }

    /**
     * Null-safe helper for cancellation: loads the slots for an instance via the
     * repository and returns the end time of the latest slot. Returns {@code null}
     * when no slots exist (anomaly).
     *
     * <p>Must be called <em>before</em> {@code slotRepository.deleteByInstance()} to
     * ensure the slot rows are still present in the database.</p>
     */
    private LocalTime slotEndTimeFromInstance(ReservInstance instance) {
        return slotRepository.findByInstance(instance).stream()
            .map(s -> s.getTimeSlot().getEndTime())
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    /**
     * Resolves the set of classroom internal IDs that participate in conflict checks
     * for the provided classroom. The rule is explicit and non-recursive (direct
     * parent/child only):
     *
     * <ul>
     *   <li>If the classroom is a parent (no {@code linkedRoom}): returns
     *       {@code { parent } ∪ directChildren }.</li>
     *   <li>If the classroom is a child ({@code linkedRoom != null}): returns
     *       {@code { child, parent }} (direct parent only).</li>
     *   <li>If the classroom does not participate in a parent/child relation:
     *       returns {@code { itself }}.</li>
     * </ul>
     *
     * This method centralizes the business rule so every conflict or availability
     * check calls it and repositories stay declarative (`IN (:scope)`).
     *
     * @param classroomId internal PK of the classroom
     * @return set of internal classroom IDs composing the conflict scope
     */
    private Set<Long> resolveConflictClassroomScope(Long classroomId) {
        var scope = new HashSet<Long>();
        // Try to load the classroom; if absent, fall back to the provided id
        Classroom c = classroomRepository.findById(classroomId).orElse(null);
        if (c == null) {
            scope.add(classroomId);
            return scope;
        }

        scope.add(c.getId());

        // If this classroom is a child (points to a parent), include its direct parent
        Classroom parent = c.getLinkedRoom();
        if (parent != null) {
            if (parent.getId() != null)
                scope.add(parent.getId());
            return scope;
        }

        // Otherwise, it's a parent (or standalone) — include direct children (non-recursive)
        List<Classroom> children = classroomRepository.findByLinkedRoom_Id(c.getId());
        for (Classroom child : children) {
            if (child.getId() != null)
                scope.add(child.getId());
        }

        return scope;
    }
}
