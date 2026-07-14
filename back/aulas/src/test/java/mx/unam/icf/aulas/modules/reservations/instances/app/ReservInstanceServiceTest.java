package mx.unam.icf.aulas.modules.reservations.instances.app;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;
import mx.unam.icf.aulas.modules.academic.timeslots.infrastructure.TimeSlotRepository;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReassignRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.app.mappers.ReservInstanceMapper;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.ClassroomType;
import mx.unam.icf.aulas.modules.resources.classrooms.infrastructure.ClassroomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReservInstanceService}, focused on the unified conflict scope
 * ({@code resolveConflictClassroomScope}) that centralizes the parent/child business
 * rule so every conflict-checking call site ({@code save}, {@code reassign}) expresses
 * it the same way.
 *
 * <p>These tests exercise {@code save} and {@code reassign} — the entry points that
 * resolve a scope and query it — rather than the private resolver directly, and stop
 * at the {@code DomainException} raised by a positive conflict so no unrelated mocking
 * (mapper, event publisher, history) is required.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReservInstanceServiceTest {

    @Mock private ReservInstanceRepository   reservInstanceRepository;
    @Mock private ReservInstanceMapper       mapper;
    @Mock private ReservationGroupRepository groupRepository;
    @Mock private ClassroomRepository        classroomRepository;
    @Mock private TimeSlotRepository         timeSlotRepository;

    private ReservInstanceService service;

    private LocalDate reservationDate;
    private Semester  semester;
    private User      user;
    private ReservationGroup group;

    private Classroom parent;
    private Classroom child;

    private TimeSlot ts1;
    private TimeSlot ts2;
    private TimeSlot ts3;

    @BeforeEach
    void setUp() {
        service = new ReservInstanceService(
                reservInstanceRepository, mapper, groupRepository, classroomRepository,
                timeSlotRepository, null, null, null, null, null, null, null, null, null);

        reservationDate = LocalDate.now().plusMonths(1);
        while (reservationDate.getDayOfWeek() == DayOfWeek.SUNDAY)
            reservationDate = reservationDate.plusDays(1);

        semester = new Semester();
        semester.setStartDate(LocalDate.now());
        semester.setEndDate(reservationDate.plusMonths(1));

        user = new User();
        user.setId(1L);
        user.setUuid(UUID.randomUUID());

        group = new ReservationGroup();
        group.setId(1L);
        group.setUuid(UUID.randomUUID());
        group.setUser(user);
        group.setSemester(semester);
        group.setStatus(ReservationGroupStatus.ACTIVE);
        group.setDaysOfWeek(Set.of(reservationDate.getDayOfWeek()));

        parent = new Classroom();
        parent.setId(10L);
        parent.setUuid(UUID.randomUUID());
        parent.setName("Aula 100");
        parent.setCapacity(60L);
        parent.setType(ClassroomType.AULA);
        parent.setIsActive(true);

        child = new Classroom();
        child.setId(11L);
        child.setUuid(UUID.randomUUID());
        child.setName("Aula 100-A");
        child.setCapacity(30L);
        child.setType(ClassroomType.AULA);
        child.setIsActive(true);
        child.setLinkedRoom(parent);

        ts1 = new TimeSlot(1, LocalTime.of(7, 0), LocalTime.of(7, 30));
        ts2 = new TimeSlot(2, LocalTime.of(7, 30), LocalTime.of(8, 0));
        ts3 = new TimeSlot(3, LocalTime.of(8, 0), LocalTime.of(8, 30));
    }

    private ReservInstanceRequestDTO bookingDto(UUID classroomUuid, List<Integer> timeSlotIds) {
        return new ReservInstanceRequestDTO(
                group.getUuid(), classroomUuid, reservationDate, timeSlotIds, 25, "Clase");
    }

    // ── save() — bidirectional blocking via the unified scope ───────────────────

    @Test
    void save_blocksBooking_whenChildRequestedAndParentAlreadyHoldsTheSlot() {
        lenient().when(groupRepository.findByUuid(group.getUuid())).thenReturn(Optional.of(group));
        when(classroomRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
        when(classroomRepository.findById(child.getId())).thenReturn(Optional.of(child));
        when(timeSlotRepository.findById(1)).thenReturn(Optional.of(ts1));

        when(reservInstanceRepository.existsConflictInScope(anyList(), eq(reservationDate), anyList()))
                .thenReturn(true);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.save(bookingDto(child.getUuid(), List.of(1)), user.getUuid()))
                .withMessageContaining("already has a reservation");

        ArgumentCaptor<List<Long>> scopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservInstanceRepository).existsConflictInScope(scopeCaptor.capture(), eq(reservationDate), anyList());
        assertThat(scopeCaptor.getValue()).containsExactlyInAnyOrder(child.getId(), parent.getId());

        verify(reservInstanceRepository, never()).save(any());
    }

    @Test
    void save_blocksBooking_whenParentRequestedAndAChildAlreadyHoldsTheSlot() {
        lenient().when(groupRepository.findByUuid(group.getUuid())).thenReturn(Optional.of(group));
        when(classroomRepository.findByUuid(parent.getUuid())).thenReturn(Optional.of(parent));
        when(classroomRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(classroomRepository.findByLinkedRoom_Id(parent.getId())).thenReturn(List.of(child));
        when(timeSlotRepository.findById(1)).thenReturn(Optional.of(ts1));

        when(reservInstanceRepository.existsConflictInScope(anyList(), eq(reservationDate), anyList()))
                .thenReturn(true);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.save(bookingDto(parent.getUuid(), List.of(1)), user.getUuid()))
                .withMessageContaining("already has a reservation");

        ArgumentCaptor<List<Long>> scopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservInstanceRepository).existsConflictInScope(scopeCaptor.capture(), eq(reservationDate), anyList());
        assertThat(scopeCaptor.getValue()).containsExactlyInAnyOrder(parent.getId(), child.getId());

        verify(reservInstanceRepository, never()).save(any());
    }

    // ── save() — partial overlap on a multi-slot request ─────────────────────────

    @Test
    void save_blocksBooking_whenOnlyOneOfSeveralRequestedSlotsConflictsWithinScope() {
        lenient().when(groupRepository.findByUuid(group.getUuid())).thenReturn(Optional.of(group));
        when(classroomRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
        when(classroomRepository.findById(child.getId())).thenReturn(Optional.of(child));
        when(timeSlotRepository.findById(1)).thenReturn(Optional.of(ts1));
        when(timeSlotRepository.findById(2)).thenReturn(Optional.of(ts2));
        when(timeSlotRepository.findById(3)).thenReturn(Optional.of(ts3));

        // Simulates a real IN(:timeSlotIds) match on just slot #2 of the 3 requested —
        // the JPQL semantics live in the query itself, but the service must forward the
        // *entire* requested block untouched so a single overlapping slot blocks the booking.
        when(reservInstanceRepository.existsConflictInScope(anyList(), eq(reservationDate), anyList()))
                .thenReturn(true);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.save(bookingDto(child.getUuid(), List.of(1, 2, 3)), user.getUuid()));

        ArgumentCaptor<List<Integer>> slotsCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservInstanceRepository).existsConflictInScope(anyList(), eq(reservationDate), slotsCaptor.capture());
        assertThat(slotsCaptor.getValue()).containsExactly(1, 2, 3);
    }

    // ── reassign() — bidirectional blocking with self-exclusion ─────────────────

    @Test
    void reassign_blocksMove_whenDestinationIsChildAndParentAlreadyHoldsTheSlot() {
        ReservInstance instance = new ReservInstance();
        instance.setId(99L);
        instance.setUuid(UUID.randomUUID());
        instance.setStatus(ReservInstanceStatus.ACTIVE);
        instance.setDate(reservationDate);

        when(reservInstanceRepository.findByUuid(instance.getUuid())).thenReturn(Optional.of(instance));
        when(classroomRepository.findByUuid(child.getUuid())).thenReturn(Optional.of(child));
        when(classroomRepository.findById(child.getId())).thenReturn(Optional.of(child));
        when(timeSlotRepository.findById(1)).thenReturn(Optional.of(ts1));
        when(timeSlotRepository.findById(2)).thenReturn(Optional.of(ts2));

        when(reservInstanceRepository.existsConflictExcludingInScope(
                anyList(), eq(reservationDate), anyList(), eq(instance.getId())))
                .thenReturn(true);

        ReassignRequestDTO dto = new ReassignRequestDTO(child.getUuid(), List.of(1, 2));

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.reassign(instance.getUuid(), dto))
                .withMessageContaining("target classroom already has a reservation");

        ArgumentCaptor<List<Long>> scopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservInstanceRepository).existsConflictExcludingInScope(
                scopeCaptor.capture(), eq(reservationDate), anyList(), eq(instance.getId()));
        assertThat(scopeCaptor.getValue()).containsExactlyInAnyOrder(child.getId(), parent.getId());
    }
}
