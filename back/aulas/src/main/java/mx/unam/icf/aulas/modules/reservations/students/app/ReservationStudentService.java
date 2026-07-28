package mx.unam.icf.aulas.modules.reservations.students.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentResponseDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentUploadResponseDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.DuplicateStudentException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.EmptyStudentListException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.StudentListNotFoundException;
import mx.unam.icf.aulas.modules.reservations.students.domain.Student;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service orchestrating student roster uploads, PDF export, and the JSON
 * student-list read used by the admin-only "view students" feature.
 *
 * <p>A roster belongs to a {@link ReservationGroup} (the recurring reservation), not to
 * any individual {@link ReservInstance} date occurrence — the roster is the same across
 * every session of the group. The file is stored as {@code {group_uuid}.xlsx} via the
 * kernel's {@link FileStorageService}. This design accepts that the system keeps no
 * per-date history: both {@link #generatePdf} and {@link #listStudents} always reflect
 * the group's <em>current</em> roster, even when queried in the context of a past session.</p>
 *
 * <h3>Roster-confirmation lifecycle</h3>
 * <p>Since the atomic multipart booking flow, the roster arrives <em>with</em> the booking
 * ({@code ReservInstanceService.createBooking} validates it, stores the file, and publishes
 * the creation notification itself). {@link #upload} therefore serves <b>re-uploads</b>: for
 * a booking-created group the roster file already exists on disk, so {@code firstConfirmation}
 * is {@code false} and no duplicate notification fires. The first-confirmation branch remains
 * for legacy groups created before the roster became mandatory at booking time.</p>
 *
 * @author Ithera
 * @version 2.1
 */
@Service
@RequiredArgsConstructor
public class ReservationStudentService {

    private final ReservationGroupRepository  groupRepository;
    private final ReservInstanceRepository    reservInstanceRepository;
    private final UserRepository              userRepository;
    private final FileStorageService          fileStorage;
    private final StudentListStorageProperties properties;
    private final StudentExcelReader          excelReader;
    private final StudentRosterValidator      rosterValidator;
    private final StudentListPdfGenerator     pdfGenerator;
    private final ApplicationEventPublisher   eventPublisher;

    /**
     * Validates and stores a student roster for a reservation group, confirming the
     * group to {@code ACTIVE} on first upload when a roster file did not previously exist.
     *
     * <p>Validation order (any failure aborts the whole operation — nothing is
     * persisted or written to disk):</p>
     * <ol>
     *   <li>The group must exist and belong to {@code principalUuid} (or the caller is ADMIN).</li>
     *   <li>The file must start with the OOXML magic number.</li>
     *   <li>The file must parse as a workbook with at least one real data row.</li>
     *   <li>No two rows may share the same student full name.</li>
     * </ol>
     *
     * <p>Only once every check passes is the group confirmed in the database, and only
     * as the transaction's <em>last</em> step is the file written to disk — if that write
     * fails, the confirmation rolls back with it (no orphaned file, no silently-confirmed
     * group without a readable roster).</p>
     *
     * @param groupUuid     public UUID of the reservation group the roster belongs to
     * @param fileBytes     raw {@code .xlsx} bytes as uploaded
     * @param principalUuid public UUID of the authenticated user
     * @param isAdmin       {@code true} when the caller holds the ADMIN role (bypasses ownership check)
     * @return the number of students accepted
     * @throws ResourceNotFoundException    when the group does not exist
     * @throws AccessDeniedException        when a non-admin uploads to a group they do not own
     * @throws InvalidExcelFileException    when the file is not a valid {@code .xlsx} document
     * @throws EmptyStudentListException    when the workbook has no real data rows
     * @throws DuplicateStudentException    when the same student name appears twice
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentUploadResponseDTO upload(UUID groupUuid, byte[] fileBytes, UUID principalUuid, boolean isAdmin) {
        ReservationGroup group = groupRepository.findByUuid(groupUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_GROUP_NOT_FOUND, "Reservation group not found: " + groupUuid));

        if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only upload a roster for your own reservation groups");

        // Full validation pipeline (magic number, parse, non-empty, duplicates) is shared
        // with the atomic booking flow via StudentRosterValidator.
        List<Student> students = rosterValidator.validate(fileBytes);

        // Determine first confirmation by checking whether a roster file already exists
        // on disk. If no file existed before this upload, this is the first confirmation.
        boolean firstConfirmation = !fileStorage.exists(properties.getStorageDir(), rosterFileName(groupUuid));

        // Persist the ACTIVE status (groups are created ACTIVE in the new flow).
        group.setStatus(ReservationGroupStatus.ACTIVE);
        groupRepository.save(group);

        // Database write happens first; the file write is the transaction's LAST line so a
        // storage failure (FileStorageException, a RuntimeException) rolls the confirmation back.
        fileStorage.store(properties.getStorageDir(), rosterFileName(groupUuid), fileBytes);

        if (firstConfirmation)
            notifyAdmins(group);

        return new StudentUploadResponseDTO(students.size());
    }

    /**
     * Renders the current roster of a reservation group as a PDF.
     *
     * @param groupUuid public UUID of the reservation group
     * @return PDF bytes ready to be sent as {@code application/pdf}
     * @throws ResourceNotFoundException  when the group does not exist
     * @throws StudentListNotFoundException when no roster has been uploaded for the group
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID groupUuid) {
        ReservationGroup group = groupRepository.findByUuid(groupUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_GROUP_NOT_FOUND, "Reservation group not found: " + groupUuid));

        // A PDF with no roster makes no sense: absence of the file is a 404 here, unlike
        // listStudents (below), which treats absence as a legitimate empty result.
        byte[] fileBytes = fileStorage.load(properties.getStorageDir(), rosterFileName(groupUuid))
                .orElseThrow(() -> new StudentListNotFoundException(ErrorCode.ROSTER_NOT_FOUND,
                        "No student roster has been uploaded for reservation group: " + groupUuid));

        List<Student> students = parseRoster(fileBytes);

        StudentRosterContext context = new StudentRosterContext(
                resolveGroupLabel(group), group.getUser().getFullName(), students.size());

        return pdfGenerator.generate(students, context);
    }

    /**
     * Returns the current roster of a reservation group as plain student data, for the
     * admin-only "view students" JSON endpoint. Reuses the exact same Excel-parsing logic
     * as {@link #generatePdf} via {@link #parseRoster}.
     *
     * <p>Unlike {@link #generatePdf}, a missing roster file is <b>not</b> an error here: it
     * is a legitimate state for legacy groups created before the roster became mandatory,
     * and is reported as an empty list so the frontend can render an empty state. A file
     * that exists but fails to parse (corrupted workbook, I/O failure) is a genuine
     * infrastructure fault and is left to propagate as an unhandled exception (mapped to a
     * 500 by the global exception handler) rather than being silently swallowed into an
     * empty list — the two situations must not be confused.</p>
     *
     * @param groupUuid public UUID of the reservation group
     * @return the group's students, or an empty list if no roster has been uploaded
     * @throws ResourceNotFoundException when the group does not exist
     */
    @Transactional(readOnly = true)
    public List<StudentResponseDTO> listStudents(UUID groupUuid) {
        groupRepository.findByUuid(groupUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_GROUP_NOT_FOUND, "Reservation group not found: " + groupUuid));

        Optional<byte[]> fileBytes = fileStorage.load(properties.getStorageDir(), rosterFileName(groupUuid));
        if (fileBytes.isEmpty())
            return List.of();

        return parseRoster(fileBytes.get()).stream()
                .map(s -> new StudentResponseDTO(s.firstName(), s.lastName(), s.email()))
                .toList();
    }

    /**
     * Checks whether a reservation group already has a roster on disk, without reading it.
     * Used by the frontend to conditionally show the "Descargar PDF" action.
     *
     * @param groupUuid public UUID of the reservation group
     * @return {@code true} if a roster file exists
     */
    @Transactional(readOnly = true)
    public boolean exists(UUID groupUuid) {
        return fileStorage.exists(properties.getStorageDir(), rosterFileName(groupUuid));
    }

    // ── Roster parsing ───────────────────────────────────────────────────────

    /**
     * Parses raw {@code .xlsx} bytes into the ordered list of students they contain.
     *
     * <p>Pure transformation — it does not know or care whether the file exists on disk;
     * that decision (404 vs. empty list) belongs to each public entry point above. Any
     * parsing failure (corrupted workbook, unreadable structure) propagates from
     * {@link StudentExcelReader#read} unchanged, so callers must not catch it here.</p>
     *
     * @param fileBytes raw {@code .xlsx} bytes already loaded from storage
     * @return students in file order
     */
    private List<Student> parseRoster(byte[] fileBytes) {
        return excelReader.read(fileBytes).stream()
                .map(ParsedStudentRow::student)
                .toList();
    }

    // ── Notification ─────────────────────────────────────────────────────────

    /**
     * Publishes the "reservation created" notification once the group's roster is
     * confirmed for the first time. Re-resolves classroom/date/time-block from the
     * group's instances because {@code createBooking} deliberately does not capture
     * this data for later use — the notification is fully decoupled from booking time.
     */
    private void notifyAdmins(ReservationGroup group) {
        List<ReservInstance> instances = reservInstanceRepository.findByGroupUuidOrderByDateAsc(group.getUuid());
        if (instances.isEmpty())
            return; // defensive: a group reaching ACTIVE always has at least one instance

        ReservInstance first = instances.getFirst();
        List<String> adminEmails = userRepository.findByRoleName("ADMIN")
                .stream().map(User::getEmail).collect(Collectors.toList());

        eventPublisher.publishEvent(new ReservInstanceCreatedEventDTO(
                group.getUser().getEmail(),
                group.getUser().getFullName(),
                first.getClassroom().getName(),
                first.getDate(),
                earliestStartTime(first),
                latestEndTime(first),
                group.getUuid(),
                adminEmails
        ));
    }

    private LocalTime earliestStartTime(ReservInstance instance) {
        return instance.getSlots().stream()
                .map(slot -> slot.getTimeSlot().getStartTime())
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalTime latestEndTime(ReservInstance instance) {
        return instance.getSlots().stream()
                .map(slot -> slot.getTimeSlot().getEndTime())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // ── Naming ────────────────────────────────────────────────────────────────

    private String resolveGroupLabel(ReservationGroup group) {
        List<ReservInstance> instances = reservInstanceRepository.findByGroupUuidOrderByDateAsc(group.getUuid());
        return instances.isEmpty()
                ? "Reservación " + group.getUuid()
                : instances.getFirst().getClassroom().getName();
    }

    private String rosterFileName(UUID groupUuid) {
        return groupUuid + ".xlsx";
    }
}
