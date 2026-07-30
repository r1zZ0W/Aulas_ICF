package mx.unam.icf.aulas.modules.reservations.students.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.reservations.students.app.ReservationStudentService;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentResponseDTO;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentUploadResponseDTO;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the student-roster sub-resource of a reservation group.
 *
 * <p>Exposed under {@code /api/v1/reservations/groups/{groupUuid}/students}. Since the
 * booking endpoint became multipart and receives the mandatory roster itself
 * ({@code POST /api/v1/reservations/booking}), this upload endpoint serves
 * <em>re-uploads</em> — replacing the roster of an already-created group.</p>
 *
 */
@RestController
@RequestMapping("/api/v1/reservations/groups/{groupUuid}/students")
@RequiredArgsConstructor
public class StudentListController implements ResponseHandler {

    private final ReservationStudentService service;

    /**
     * Uploads (or replaces) the student roster for a reservation group.
     * POST /api/v1/reservations/groups/{groupUuid}/students (multipart/form-data)
     *
     * <p>On first successful upload the group transitions from {@code PENDING_ROSTER} to
     * {@code ACTIVE} and admins are notified. Only the owning teacher or an ADMIN may
     * upload; all validation failures abort the operation without persisting anything.</p>
     *
     * @param groupUuid public UUID of the reservation group the roster belongs to
     * @param file      the uploaded {@code .xlsx} file (form field name {@code file})
     * @param principal authenticated user (used for ownership/role checks)
     * @return the number of students accepted
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the group does not exist (404)
     * @throws org.springframework.security.access.AccessDeniedException                    when a non-admin uploads to a group they do not own (403)
     * @throws InvalidExcelFileException                                                    when the file is not a valid .xlsx document (400)
     * @throws mx.unam.icf.aulas.modules.reservations.students.app.exceptions.EmptyStudentListException  when the workbook has no data rows (422)
     * @throws mx.unam.icf.aulas.modules.reservations.students.app.exceptions.DuplicateStudentException  when the same student name appears twice (422)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StudentUploadResponseDTO>> upload(
            @PathVariable UUID groupUuid,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImp principal) {

        boolean isAdmin = "ADMIN".equals(principal.getRoleName());
        return ok(service.upload(groupUuid, readBytes(file), principal.getUuid(), isAdmin));
    }

    /**
     * Checks whether a roster has been uploaded for a reservation group, without
     * downloading it. Lets the frontend conditionally show the "Descargar PDF" action.
     * GET /api/v1/reservations/groups/{groupUuid}/students/exists
     *
     * @param groupUuid public UUID of the reservation group
     * @return {@code true} when a roster file exists on disk
     */
    @GetMapping(value = "/exists", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable UUID groupUuid) {
        return ok(service.exists(groupUuid));
    }

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

    /**
     * Reads the multipart file into memory, translating a transport-level
     * {@link IOException} into the same 400 response used for structurally invalid files.
     */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidExcelFileException(ErrorCode.ROSTER_FILE_UNREADABLE, "Could not read the uploaded file.", e);
        }
    }
}
