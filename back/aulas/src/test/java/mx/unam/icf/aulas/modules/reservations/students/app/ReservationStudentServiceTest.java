package mx.unam.icf.aulas.modules.reservations.students.app;

import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.students.app.dtos.StudentResponseDTO;
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
                excelReader, null, null);

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
