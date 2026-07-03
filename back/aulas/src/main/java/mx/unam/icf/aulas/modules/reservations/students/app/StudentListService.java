package mx.unam.icf.aulas.modules.reservations.students.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service orchestrating student roster uploads and PDF export.
 *
 * <p>A roster belongs to a {@link ReservationGroup} (the recurring reservation), not to
 * any individual {@link ReservInstance} date occurrence — the roster is the same across
 * every session of the group. The file is stored as {@code {group_uuid}.xlsx} via the
 * kernel's {@link FileStorageService}. This design accepts that the system keeps no
 * per-date history: {@link #generatePdf} always reflects the group's <em>current</em>
 * roster, even when queried in the context of a past session.</p>
 *
 * <h3>Roster-confirmation lifecycle</h3>
 * <p>{@code ReservInstanceService.createBooking} persists new groups as
 * {@link ReservationGroupStatus#PENDING_ROSTER} and does <em>not</em> notify admins.
 * {@link #upload} is the only path that transitions a group to
 * {@link ReservationGroupStatus#ACTIVE} and fires {@link ReservInstanceCreatedEventDTO} —
 * and only the first time (subsequent re-uploads on an already-{@code ACTIVE} group update
 * the roster silently, since the reservation was already confirmed and notified).</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class StudentListService {

    /** OOXML (.xlsx / .docx / .zip-based) magic number: {@code PK\x03\x04}. */
    private static final byte[] OOXML_MAGIC_NUMBER = {0x50, 0x4B, 0x03, 0x04};

    private final ReservationGroupRepository  groupRepository;
    private final ReservInstanceRepository    reservInstanceRepository;
    private final UserRepository              userRepository;
    private final FileStorageService          fileStorage;
    private final StudentListStorageProperties properties;
    private final StudentExcelReader          excelReader;
    private final StudentListPdfGenerator     pdfGenerator;
    private final ApplicationEventPublisher   eventPublisher;

    /**
     * Validates and stores a student roster for a reservation group, confirming the
     * group ({@code PENDING_ROSTER} → {@code ACTIVE}) on first upload.
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
                .orElseThrow(() -> new ResourceNotFoundException("Reservation group not found: " + groupUuid));

        if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only upload a roster for your own reservation groups");

        validateMagicNumber(fileBytes);

        List<ParsedStudentRow> rows = excelReader.read(fileBytes);
        if (rows.isEmpty())
            throw new EmptyStudentListException("The uploaded roster has no student rows.");

        List<Student> students = rejectDuplicatesOrCollect(rows);

        boolean firstConfirmation = group.getStatus() == ReservationGroupStatus.PENDING_ROSTER;
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
                .orElseThrow(() -> new ResourceNotFoundException("Reservation group not found: " + groupUuid));

        byte[] fileBytes = fileStorage.load(properties.getStorageDir(), rosterFileName(groupUuid))
                .orElseThrow(() -> new StudentListNotFoundException(
                        "No student roster has been uploaded for reservation group: " + groupUuid));

        List<Student> students = excelReader.read(fileBytes).stream()
                .map(ParsedStudentRow::student)
                .toList();

        StudentRosterContext context = new StudentRosterContext(
                resolveGroupLabel(group), group.getUser().getFullName(), students.size());

        return pdfGenerator.generate(students, context);
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

    // ── Validation helpers ───────────────────────────────────────────────────

    /**
     * Confirms the first four bytes match the OOXML magic number without exhausting any
     * stream — the caller always hands over the full {@code byte[]} already read into memory
     * (from {@code MultipartFile#getBytes()}), so Apache POI can freely re-read it afterward
     * from a fresh {@code ByteArrayInputStream}.
     */
    private void validateMagicNumber(byte[] fileBytes) {
        if (fileBytes.length < OOXML_MAGIC_NUMBER.length)
            throw new InvalidExcelFileException("The uploaded file is empty or too small to be a valid .xlsx document.");

        for (int i = 0; i < OOXML_MAGIC_NUMBER.length; i++) {
            if (fileBytes[i] != OOXML_MAGIC_NUMBER[i])
                throw new InvalidExcelFileException(
                        "The uploaded file is not a valid .xlsx (OOXML) document. " +
                        "Renamed .xls files and other formats are rejected.");
        }
    }

    /**
     * Detects intra-file duplicate students by full name using a {@link HashSet}, aborting
     * at the first collision with the offending row and name.
     */
    private List<Student> rejectDuplicatesOrCollect(List<ParsedStudentRow> rows) {
        Set<String> seenNames = new HashSet<>();
        List<Student> students = new ArrayList<>(rows.size());

        for (ParsedStudentRow row : rows) {
            Student student = row.student();
            if (!seenNames.add(student.duplicateKey()))
                throw new DuplicateStudentException(row.rowNumber(), student.fullName());
            students.add(student);
        }

        return students;
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
