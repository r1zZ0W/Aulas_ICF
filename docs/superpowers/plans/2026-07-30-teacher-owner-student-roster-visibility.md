# Teacher-Owner Student Roster Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a teacher who owns a reservation view their own reservation's student roster (previously ADMIN-only), while deleting the unused PDF-export feature entirely.

**Architecture:** Extend `ReservationStudentService.listStudents` with the same owner-or-admin ownership check `upload` already uses, wire it through `StudentListController.list` by dropping the `@PreAuthorize("hasRole('ADMIN')")` and injecting the authenticated principal, then relax the frontend button gate from `isAdmin` to `isAdmin || isOwner`. Separately, delete the PDF-export code path (controller method, service method, generator classes, dedicated exception/error codes, the `openpdf` Maven dependency) since it has no caller anywhere in the frontend, for any role.

**Tech Stack:** Spring Boot (Java), JUnit 5 + Mockito + AssertJ for backend unit tests, React + Vite (no frontend test runner configured — verify via manual browser check per project convention).

## Global Constraints

- Follow the existing ownership-check pattern verbatim (from `ReservationStudentService.upload`, `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentService.java:104-105`): `if (!isAdmin && !group.getUser().getUuid().equals(principalUuid)) throw new AccessDeniedException(...)`.
- Do not touch `POST /students` (upload), `GET /students/exists`, or any non-roster reservation endpoint — out of scope.
- No new frontend UI, no new npm/pnpm test tooling — this project has no JS test runner configured; frontend verification is manual (`pnpm dev` + browser), per project convention (see CLAUDE.md: "For UI or frontend changes... test in a browser before reporting complete").
- Backend module is Maven; run tests with `mvn test` from `back/aulas`.

---

### Task 1: Add owner-or-admin authorization to `ReservationStudentService.listStudents`

**Files:**
- Modify: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentService.java:173-185`
- Test: `back/aulas/src/test/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentServiceTest.java` (new file)

**Interfaces:**
- Consumes: `ReservationGroupRepository.findByUuid(UUID): Optional<ReservationGroup>`, `ReservationGroup.getUser(): User`, `User.getUuid(): UUID`, `FileStorageService.load(String, String): Optional<byte[]>`, `StudentExcelReader.read(byte[]): List<ParsedStudentRow>`, `StudentResponseDTO(String, String, String)`.
- Produces: `ReservationStudentService.listStudents(UUID groupUuid, UUID principalUuid, boolean isAdmin): List<StudentResponseDTO>` — new signature (was `listStudents(UUID groupUuid)`). Task 2's controller change depends on this exact signature.

This is the only method in the codebase currently calling `listStudents`, so changing its signature has one call site to update (done in Task 2).

- [ ] **Step 1: Write the failing tests**

Create `back/aulas/src/test/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentServiceTest.java`:

```java
package mx.unam.icf.aulas.modules.reservations.students.app;

import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentResponseDTO;
import mx.unam.icf.aulas.modules.reservations.students.domain.Student;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReservationStudentService#listStudents}, covering the
 * owner-or-admin authorization gate added to grant a teacher visibility into
 * their own reservation's roster.
 */
@ExtendWith(MockitoExtension.class)
class ReservationStudentServiceTest {

    @Mock private ReservationGroupRepository groupRepository;
    @Mock private FileStorageService         fileStorage;
    @Mock private StudentExcelReader         excelReader;
    @Mock private StudentListStorageProperties properties;

    private ReservationStudentService service;

    private UUID groupUuid;
    private UUID ownerUuid;
    private ReservationGroup group;

    @BeforeEach
    void setUp() {
        service = new ReservationStudentService(
                groupRepository, null, null, fileStorage, properties,
                excelReader, null, null, null);

        groupUuid = UUID.randomUUID();
        ownerUuid = UUID.randomUUID();

        User owner = new User();
        owner.setId(1L);
        owner.setUuid(ownerUuid);

        group = new ReservationGroup();
        group.setId(1L);
        group.setUuid(groupUuid);
        group.setUser(owner);

        lenient().when(properties.getStorageDir()).thenReturn("student-lists");
    }

    @Test
    void ownerTeacherCanListTheirOwnRoster() {
        when(groupRepository.findByUuid(groupUuid)).thenReturn(Optional.of(group));
        when(fileStorage.load("student-lists", groupUuid + ".xlsx")).thenReturn(Optional.empty());

        List<StudentResponseDTO> result = service.listStudents(groupUuid, ownerUuid, false);

        assertThat(result).isEmpty();
    }

    @Test
    void nonOwnerTeacherIsDeniedAccess() {
        when(groupRepository.findByUuid(groupUuid)).thenReturn(Optional.of(group));

        UUID someoneElse = UUID.randomUUID();

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> service.listStudents(groupUuid, someoneElse, false));
    }

    @Test
    void adminCanListAnyGroupsRoster() {
        when(groupRepository.findByUuid(groupUuid)).thenReturn(Optional.of(group));
        when(fileStorage.load("student-lists", groupUuid + ".xlsx")).thenReturn(Optional.empty());

        UUID adminUuid = UUID.randomUUID();

        List<StudentResponseDTO> result = service.listStudents(groupUuid, adminUuid, true);

        assertThat(result).isEmpty();
    }

    @Test
    void missingGroupThrowsResourceNotFoundRegardlessOfCaller() {
        when(groupRepository.findByUuid(groupUuid)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.listStudents(groupUuid, ownerUuid, false));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl . -am test -Dtest=ReservationStudentServiceTest -f back/aulas/pom.xml`

Expected: compilation failure — `listStudents(UUID, UUID, boolean)` does not exist yet (current signature is `listStudents(UUID)`).

The `setUp()` constructor call is positional against `ReservationStudentService`'s current 9-field declaration order (`groupRepository, reservInstanceRepository, userRepository, fileStorage, properties, excelReader, rosterValidator, pdfGenerator, eventPublisher`): the test passes `groupRepository, null, null, fileStorage, properties, excelReader, null, null, null` — real mocks for `groupRepository`, `fileStorage`, `properties`, `excelReader`, and `null` for the other five fields (`reservInstanceRepository`, `userRepository`, `rosterValidator`, `pdfGenerator`, `eventPublisher`), none of which `listStudents` touches. If this task is executed after the field list has changed for any reason, re-count the fields in the source file and adjust the positional args to match before running.

- [ ] **Step 3: Add the ownership check and update the signature**

In `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentService.java`, replace:

```java
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
```

with:

```java
    @Transactional(readOnly = true)
    public List<StudentResponseDTO> listStudents(UUID groupUuid, UUID principalUuid, boolean isAdmin) {
        ReservationGroup group = groupRepository.findByUuid(groupUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESERVATION_GROUP_NOT_FOUND, "Reservation group not found: " + groupUuid));

        if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only view the roster of your own reservation groups");

        Optional<byte[]> fileBytes = fileStorage.load(properties.getStorageDir(), rosterFileName(groupUuid));
        if (fileBytes.isEmpty())
            return List.of();

        return parseRoster(fileBytes.get()).stream()
                .map(s -> new StudentResponseDTO(s.firstName(), s.lastName(), s.email()))
                .toList();
    }
```

Also update the method's Javadoc (immediately above it) — replace:

```java
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
```

with:

```java
    /**
     * Returns the current roster of a reservation group as plain student data, for the
     * "view students" JSON endpoint. Available to the owning teacher and to ADMIN users.
     *
     * <p>A missing roster file is <b>not</b> an error here: it is a legitimate state for
     * legacy groups created before the roster became mandatory, and is reported as an
     * empty list so the frontend can render an empty state. A file that exists but fails
     * to parse (corrupted workbook, I/O failure) is a genuine infrastructure fault and is
     * left to propagate as an unhandled exception (mapped to a 500 by the global exception
     * handler) rather than being silently swallowed into an empty list — the two situations
     * must not be confused.</p>
     *
     * @param groupUuid     public UUID of the reservation group
     * @param principalUuid public UUID of the authenticated user
     * @param isAdmin       {@code true} when the caller holds the ADMIN role (bypasses ownership check)
     * @return the group's students, or an empty list if no roster has been uploaded
     * @throws ResourceNotFoundException when the group does not exist
     * @throws AccessDeniedException     when a non-admin requests a group they do not own
     */
```

Note `AccessDeniedException` is already imported in this file (used by `upload`), so no new import is needed for the production code change.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -pl . -am test -Dtest=ReservationStudentServiceTest -f back/aulas/pom.xml`

Expected: all 4 tests in `ReservationStudentServiceTest` PASS.

- [ ] **Step 5: Commit**

```bash
git add back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentService.java back/aulas/src/test/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentServiceTest.java
git commit -m "feat: authorize roster listing for owning teacher or admin"
```

---

### Task 2: Wire the owner-or-admin check into `StudentListController.list`

**Files:**
- Modify: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/infrastructure/StudentListController.java:114-130`

**Interfaces:**
- Consumes: `ReservationStudentService.listStudents(UUID, UUID, boolean): List<StudentResponseDTO>` from Task 1.
- Produces: `GET /api/v1/reservations/groups/{groupUuid}/students` now returns roster data for the owning teacher or an ADMIN (was ADMIN-only), 403 otherwise.

No dedicated controller test is added here — this codebase has no existing test file for `StudentListController`, and the equivalent `upload` endpoint (which uses the identical `isAdmin = "ADMIN".equals(principal.getRoleName())` pattern) has none either. Task 1's service test is where the ownership logic is actually verified; this task is pure wiring, verified by the full `mvn test` run in Task 3 and the manual walkthrough in Task 4.

- [ ] **Step 1: Update the controller method**

In `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/infrastructure/StudentListController.java`, replace:

```java
    /**
     * Lists the students in the current roster of a reservation group as structured JSON.
     * Requires ADMIN role. GET /api/v1/reservations/groups/{groupUuid}/students
     *
     * <p>Reuses the same Excel-parsing logic as {@link #downloadPdf} (via
     * {@link ReservationStudentService#listStudents}); this endpoint exists so the admin
     * "view students" UI can render structured, searchable data instead of a PDF. Returns
     * an empty list (not a 404) when no roster has been uploaded yet.</p>
     *
     * @param groupUuid public UUID of the reservation group
     * @return the group's students, or an empty list if no roster has been uploaded
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<StudentResponseDTO>>> list(@PathVariable UUID groupUuid) {
        return ok(service.listStudents(groupUuid));
    }
```

with:

```java
    /**
     * Lists the students in the current roster of a reservation group as structured JSON.
     * Available to the owning teacher and to ADMIN users.
     * GET /api/v1/reservations/groups/{groupUuid}/students
     *
     * <p>This endpoint lets the "view students" UI render structured, searchable data.
     * Returns an empty list (not a 404) when no roster has been uploaded yet.</p>
     *
     * @param groupUuid public UUID of the reservation group
     * @param principal authenticated user (used for ownership/role checks)
     * @return the group's students, or an empty list if no roster has been uploaded
     * @throws org.springframework.security.access.AccessDeniedException when a non-admin
     *         requests a group they do not own (403)
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<StudentResponseDTO>>> list(
            @PathVariable UUID groupUuid,
            @AuthenticationPrincipal UserDetailsImp principal) {
        boolean isAdmin = "ADMIN".equals(principal.getRoleName());
        return ok(service.listStudents(groupUuid, principal.getUuid(), isAdmin));
    }
```

`@AuthenticationPrincipal` and `UserDetailsImp` are already imported in this file (used by `upload`), so no new imports are needed. `@PreAuthorize` remains imported/used by `downloadPdf` at this point in the plan — it is removed in Task 3 when that method is deleted.

- [ ] **Step 2: Compile-check**

Run: `mvn -pl . -am compile -f back/aulas/pom.xml`

Expected: BUILD SUCCESS (this task has no new automated test; a clean compile is the pass criterion here, full behavioral verification happens in Task 3's test run and Task 4's manual walkthrough).

- [ ] **Step 3: Commit**

```bash
git add back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/infrastructure/StudentListController.java
git commit -m "feat: allow owning teacher to fetch their reservation's roster"
```

---

### Task 3: Delete the unused PDF-export feature

**Files:**
- Modify: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/infrastructure/StudentListController.java` (remove `downloadPdf` and now-unused imports)
- Modify: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentService.java` (remove `generatePdf`, `resolveGroupLabel`, the `pdfGenerator` field, and PDF references in the class Javadoc)
- Delete: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/StudentListPdfGenerator.java`
- Delete: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/infrastructure/OpenPdfStudentListGenerator.java`
- Delete: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/StudentRosterContext.java`
- Delete: `back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/exceptions/StudentListNotFoundException.java`
- Modify: `back/aulas/src/main/java/mx/unam/icf/aulas/kernel/domain/exceptions/ErrorCode.java` (remove `ROSTER_NOT_FOUND`, `ROSTER_PDF_FAILED`)
- Modify: `back/aulas/pom.xml` (remove `openpdf` dependency and version property)
- Modify: `back/aulas/src/test/java/mx/unam/icf/aulas/modules/reservations/students/app/ReservationStudentServiceTest.java` (drop one constructor arg, see Step 6)

**Interfaces:**
- Consumes: nothing new.
- Produces: `GET /api/v1/reservations/groups/{groupUuid}/students/pdf` no longer exists (404 route-not-found, not an app-level exception). No other task depends on any PDF-related symbol after this task.

- [ ] **Step 1: Remove the PDF endpoint from the controller**

In `StudentListController.java`, delete this entire method (and the Javadoc directly above it):

```java
    /**
     * Downloads the current student roster as a PDF. Requires ADMIN role.
     * GET /api/v1/reservations/groups/{groupUuid}/students/pdf
     *
     * <p>The PDF always reflects the group's <em>current</em> roster — there is no
     * per-date historical snapshot (see {@link ReservationStudentService} class docs).</p>
     *
     * @param groupUuid public UUID of the reservation group
     * @return PDF bytes as {@code application/pdf} with a {@code Content-Disposition: attachment} header
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID groupUuid) {
        byte[] pdf = service.generatePdf(groupUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("lista-alumnos.pdf").build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
```

Then remove the now-unused imports at the top of the file:

```java
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
```

and remove `@PreAuthorize` and its import (`org.springframework.security.access.prepost.PreAuthorize`) since `list` no longer uses it after Task 2 and `downloadPdf` is gone — confirm no other method in this file still uses `@PreAuthorize` before removing the import.

Also update the class-level Javadoc (top of the file), which currently says:

```java
/**
 * REST controller for the student-roster sub-resource of a reservation group.
 *
 * <p>Exposed under {@code /api/v1/reservations/groups/{groupUuid}/students}. Since the
 * booking endpoint became multipart and receives the mandatory roster itself
 * ({@code POST /api/v1/reservations/booking}), this upload endpoint serves
 * <em>re-uploads</em> — replacing the roster of an already-created group.</p>
 *
 * <p>No class-level {@code produces} is declared (unlike {@code ReservInstanceController})
 * because this controller mixes JSON responses with a raw PDF download; each method
 * declares its own content type, mirroring {@code ReportController}.</p>
 */
```

Replace the second paragraph (the class no longer mixes JSON and PDF once `downloadPdf` is gone):

```java
/**
 * REST controller for the student-roster sub-resource of a reservation group.
 *
 * <p>Exposed under {@code /api/v1/reservations/groups/{groupUuid}/students}. Since the
 * booking endpoint became multipart and receives the mandatory roster itself
 * ({@code POST /api/v1/reservations/booking}), this upload endpoint serves
 * <em>re-uploads</em> — replacing the roster of an already-created group.</p>
 */
```

- [ ] **Step 2: Remove the PDF generation method from the service**

In `ReservationStudentService.java`, delete this entire method (and its Javadoc):

```java
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
```

and this now-orphaned private helper (only ever called from `generatePdf`):

```java
    private String resolveGroupLabel(ReservationGroup group) {
        List<ReservInstance> instances = reservInstanceRepository.findByGroupUuidOrderByDateAsc(group.getUuid());
        return instances.isEmpty()
                ? "Reservación " + group.getUuid()
                : instances.getFirst().getClassroom().getName();
    }
```

Remove the `pdfGenerator` field:

```java
    private final StudentListPdfGenerator     pdfGenerator;
```

and its import:

```java
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.StudentListNotFoundException;
```

Verify `reservInstanceRepository` is still used elsewhere in the file (it is — by `notifyAdmins`) before touching that field; do not remove it.

Update the class-level Javadoc, which currently reads:

```java
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
```

Replace with:

```java
/**
 * Application service orchestrating student roster uploads and the JSON student-list
 * read used by the "view students" feature (owning teacher or ADMIN).
 *
 * <p>A roster belongs to a {@link ReservationGroup} (the recurring reservation), not to
 * any individual {@link ReservInstance} date occurrence — the roster is the same across
 * every session of the group. The file is stored as {@code {group_uuid}.xlsx} via the
 * kernel's {@link FileStorageService}. This design accepts that the system keeps no
 * per-date history: {@link #listStudents} always reflects the group's <em>current</em>
 * roster, even when queried in the context of a past session.</p>
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
 * @version 2.2
 */
```

- [ ] **Step 3: Delete the four PDF-only source files**

```bash
git rm back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/StudentListPdfGenerator.java
git rm back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/infrastructure/OpenPdfStudentListGenerator.java
git rm back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/StudentRosterContext.java
git rm back/aulas/src/main/java/mx/unam/icf/aulas/modules/reservations/students/app/exceptions/StudentListNotFoundException.java
```

- [ ] **Step 4: Remove the PDF-only error codes**

In `back/aulas/src/main/java/mx/unam/icf/aulas/kernel/domain/exceptions/ErrorCode.java`, in the `// ── Student roster` block, replace:

```java
    // ── Student roster ───────────────────────────────────────────────────────
    ROSTER_NOT_FOUND,
    ROSTER_EMPTY,
    ROSTER_DUPLICATE_STUDENT,
    ROSTER_COUNT_MISMATCH,
    ROSTER_FILE_INVALID,
    ROSTER_FILE_UNREADABLE,
    ROSTER_PDF_FAILED,
```

with:

```java
    // ── Student roster ───────────────────────────────────────────────────────
    ROSTER_EMPTY,
    ROSTER_DUPLICATE_STUDENT,
    ROSTER_COUNT_MISMATCH,
    ROSTER_FILE_INVALID,
    ROSTER_FILE_UNREADABLE,
```

(`ROSTER_EMPTY`, `ROSTER_DUPLICATE_STUDENT`, `ROSTER_COUNT_MISMATCH`, `ROSTER_FILE_INVALID`, `ROSTER_FILE_UNREADABLE` are used by the upload/validation path and must stay.)

- [ ] **Step 5: Remove the `openpdf` Maven dependency**

In `back/aulas/pom.xml`, remove this property (around line 22):

```xml
        <openpdf.version>1.3.43</openpdf.version>
```

and this dependency block (around lines 113-117):

```xml
        <dependency>
            <groupId>com.github.librepdf</groupId>
            <artifactId>openpdf</artifactId>
            <version>${openpdf.version}</version>
        </dependency>
```

- [ ] **Step 6: Run the full backend test suite**

Run: `mvn test -f back/aulas/pom.xml`

This will fail to compile at first: `ReservationStudentServiceTest.setUp()` still passes 9 positional constructor args, but the constructor now takes only 8 (`pdfGenerator` removed). Fix it by dropping one of the trailing `null` args in the test's constructor call:

```java
        service = new ReservationStudentService(
                groupRepository, null, null, fileStorage, properties,
                excelReader, null, null);
```

Re-run `mvn test -f back/aulas/pom.xml`. Expected: BUILD SUCCESS, all tests pass, including all 4 tests in `ReservationStudentServiceTest` from Task 1.

- [ ] **Step 7: Commit**

```bash
git add -A back/aulas
git commit -m "refactor: delete unused student-roster PDF export feature"
```

---

### Task 4: Frontend — show the roster button to the owning teacher

**Files:**
- Modify: `front/icf-aulas/src/components/ReservaInfoModal/ReservaInfoModal.jsx:36-41,144-153`
- Modify: `front/icf-aulas/src/components/ReservaStudentsModal/ReservaStudentsModal.jsx:26-27`

**Interfaces:**
- Consumes: `isOwner` (already computed at `ReservaInfoModal.jsx:73`), `isAdmin` (already computed at `ReservaInfoModal.jsx:52`).
- Produces: no new exports; purely a render-condition change.

- [ ] **Step 1: Relax the button guard**

In `front/icf-aulas/src/components/ReservaInfoModal/ReservaInfoModal.jsx`, replace:

```jsx
          <div className="reserva-modal__field">
            <div className="reserva-info-modal__students-header">
              <label className="reserva-modal__label">Estudiantes inscritos</label>
              {isAdmin && (
                <button
                  type="button"
                  className="reserva-info-modal__inline-btn"
                  onClick={openStudentsModal}
                >
                  <Users size={15} />
                  Ver lista completa
                </button>
              )}
            </div>
```

with:

```jsx
          <div className="reserva-modal__field">
            <div className="reserva-info-modal__students-header">
              <label className="reserva-modal__label">Estudiantes inscritos</label>
              {(isAdmin || isOwner) && (
                <button
                  type="button"
                  className="reserva-info-modal__inline-btn"
                  onClick={openStudentsModal}
                >
                  <Users size={15} />
                  Ver lista completa
                </button>
              )}
            </div>
```

- [ ] **Step 2: Update the visibility-rules doc comment**

In the same file, replace the doc comment block above the component:

```jsx
/**
 * Read-only modal displaying the details of a reservation instance.
 *
 * Reads from `ReservInstanceResponseDTO` fields:
 *  - `reservation.motivo` → class/event name
 *  - `reservation.classroomUuid` → room lookup in `roomById`
 *  - `reservation.timeSlots` + `reservation.date` → start/end times
 *  - `reservation.numAsistentes` → attendee count (informational, not shown currently)
 *
 * Visibility rules:
 *  - Admin               → "Ver estudiantes" always (any status, past or future — opens
 *                          {@link ReservaStudentsModal}); "Cancelar" (admin mutation) +
 *                          "Reasignar" only when !isPast && status === 'ACTIVE'.
 *  - Teacher (owner)     → "Cancelar" (user mutation) only when !isPast && status === 'ACTIVE'.
 *  - Teacher (not owner) → read-only, no action buttons.
 *
 * @param {{
 *   open:        boolean,
 *   onClose:     () => void,
 *   reservation: object | null,
 *   onEdit:      () => void,
 * }} props
 */
```

with:

```jsx
/**
 * Read-only modal displaying the details of a reservation instance.
 *
 * Reads from `ReservInstanceResponseDTO` fields:
 *  - `reservation.motivo` → class/event name
 *  - `reservation.classroomUuid` → room lookup in `roomById`
 *  - `reservation.timeSlots` + `reservation.date` → start/end times
 *  - `reservation.numAsistentes` → attendee count (informational, not shown currently)
 *
 * Visibility rules:
 *  - Admin               → "Ver estudiantes" always (any status, past or future — opens
 *                          {@link ReservaStudentsModal}); "Cancelar" (admin mutation) +
 *                          "Reasignar" only when !isPast && status === 'ACTIVE'.
 *  - Teacher (owner)     → "Ver estudiantes" always; "Cancelar" (user mutation) only when
 *                          !isPast && status === 'ACTIVE'.
 *  - Teacher (not owner) → read-only, no action buttons, no "Ver estudiantes".
 *
 * @param {{
 *   open:        boolean,
 *   onClose:     () => void,
 *   reservation: object | null,
 *   onEdit:      () => void,
 * }} props
 */
```

- [ ] **Step 3: Update `ReservaStudentsModal`'s doc comment**

In `front/icf-aulas/src/components/ReservaStudentsModal/ReservaStudentsModal.jsx`, replace:

```jsx
/**
 * Admin-only, read-only modal listing the students registered for a reservation's
 * roster. Reads `reservation.groupUuid` (the roster is per-*group*, shared across every
 * date occurrence) and `reservation.attendeeCount` (already present on the reservation
 * DTO — not part of the roster response).
```

with:

```jsx
/**
 * Read-only modal listing the students registered for a reservation's roster.
 * Opened by the owning teacher or an ADMIN — the caller (`ReservaInfoModal`) is
 * responsible for that gate; this component itself is role-agnostic. Reads
 * `reservation.groupUuid` (the roster is per-*group*, shared across every date
 * occurrence) and `reservation.attendeeCount` (already present on the reservation
 * DTO — not part of the roster response).
```

- [ ] **Step 4: Manually verify in the browser**

Run: `pnpm dev` from `front/icf-aulas` (backend must be running with Task 1-3's changes applied).

Log in as a teacher who owns at least one reservation with a roster uploaded, open that reservation's info modal, and confirm the "Ver lista completa" button now appears and opens the roster correctly. Then open a reservation owned by a *different* teacher (or log in as that other teacher) and confirm the button is absent. Finally confirm an admin still sees the button on every reservation.

- [ ] **Step 5: Commit**

```bash
git add front/icf-aulas/src/components/ReservaInfoModal/ReservaInfoModal.jsx front/icf-aulas/src/components/ReservaStudentsModal/ReservaStudentsModal.jsx
git commit -m "feat: show roster button to the owning teacher, not just admins"
```

---

### Task 5: Frontend — remove the orphaned PDF error-catalog entries

**Files:**
- Modify: `front/icf-aulas/src/errors/errorCatalog.js:93,99`

**Interfaces:**
- Consumes: none.
- Produces: none — this is dead-code removal following Task 3's backend deletion of `ErrorCode.ROSTER_NOT_FOUND` and `ErrorCode.ROSTER_PDF_FAILED`.

- [ ] **Step 1: Remove the two orphaned entries**

In `front/icf-aulas/src/errors/errorCatalog.js`, replace:

```js
  // ── Student roster ───────────────────────────────────────────────────────
  ROSTER_NOT_FOUND: { text: 'Aún no se ha subido una lista de alumnos para este grupo.' },
  ROSTER_EMPTY: { text: 'La lista de alumnos está vacía o no es válida.' },
  ROSTER_DUPLICATE_STUDENT: { text: 'Hay un alumno duplicado en la lista.' },
  ROSTER_COUNT_MISMATCH: { text: 'El número de alumnos en el Excel no coincide con los asistentes indicados.' },
  ROSTER_FILE_INVALID: { text: 'El archivo no es un .xlsx válido.' },
  ROSTER_FILE_UNREADABLE: { text: 'No se pudo leer el archivo subido.' },
  ROSTER_PDF_FAILED: { text: 'No se pudo generar el PDF de la lista de alumnos.' },
```

with:

```js
  // ── Student roster ───────────────────────────────────────────────────────
  ROSTER_EMPTY: { text: 'La lista de alumnos está vacía o no es válida.' },
  ROSTER_DUPLICATE_STUDENT: { text: 'Hay un alumno duplicado en la lista.' },
  ROSTER_COUNT_MISMATCH: { text: 'El número de alumnos en el Excel no coincide con los asistentes indicados.' },
  ROSTER_FILE_INVALID: { text: 'El archivo no es un .xlsx válido.' },
  ROSTER_FILE_UNREADABLE: { text: 'No se pudo leer el archivo subido.' },
```

- [ ] **Step 2: Verify no remaining references**

Run: `grep -rn "ROSTER_NOT_FOUND\|ROSTER_PDF_FAILED" front/icf-aulas/src back/aulas/src`

Expected: no output (both codes fully removed from front and back).

- [ ] **Step 3: Commit**

```bash
git add front/icf-aulas/src/errors/errorCatalog.js
git commit -m "chore: remove orphaned PDF error-catalog entries"
```
