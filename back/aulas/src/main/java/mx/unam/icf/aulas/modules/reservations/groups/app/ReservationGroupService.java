package mx.unam.icf.aulas.modules.reservations.groups.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import mx.unam.icf.aulas.modules.reservations.groups.app.dtos.ReservationGroupRequestDTO;
import mx.unam.icf.aulas.modules.reservations.groups.app.dtos.ReservationGroupResponseDTO;
import mx.unam.icf.aulas.modules.reservations.groups.app.mappers.ReservationGroupMapper;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing the lifecycle of {@link ReservationGroup} entities.
 *
 * <p>A reservation group defines a recurring weekly pattern for a user during a semester.
 * Individual date occurrences are materialized separately as reservation instances.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class ReservationGroupService {

    private final ReservationGroupRepository repository;
    private final ReservationGroupMapper mapper;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;

    /**
     * Returns all reservation groups in the system.
     * GET /api/v1/reservation-groups
     */
    @Transactional(readOnly = true)
    public List<ReservationGroupResponseDTO> findAll() {
        return mapper.toDtoList(repository.findAll());
    }

    /**
     * Returns all reservation groups belonging to a specific user.
     *
     * @param userUuid public UUID of the target user
     */
    @Transactional(readOnly = true)
    public List<ReservationGroupResponseDTO> findByUser(UUID userUuid) {
        return mapper.toDtoList(repository.findByUserUuid(userUuid));
    }

    /**
     * Returns a single reservation group by its public UUID.
     *
     * @param uuid public UUID of the reservation group
     * @throws ResourceNotFoundException when no group matches the given UUID
     */
    @Transactional(readOnly = true)
    public ReservationGroupResponseDTO findByUuid(UUID uuid) {
        return mapper.toDto(
            repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation group not found: " + uuid))
        );
    }

    /**
     * Creates a new reservation group for the given user and semester.
     *
     * <p>Status is automatically set to {@code ACTIVE} on creation.</p>
     *
     * <p>Unless {@code isAdmin} is {@code true}, the {@code principalUuid} must match
     * {@code dto.userUuid()} to prevent a Maestro from creating groups on behalf of
     * another user (BOLA/IDOR protection).</p>
     *
     * @param dto           creation payload
     * @param principalUuid public UUID of the authenticated user making the request
     * @param isAdmin       whether the authenticated user has the ADMIN role
     * @throws AccessDeniedException     when a non-admin tries to create a group for another user
     * @throws ResourceNotFoundException when the user or semester does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservationGroupResponseDTO save(ReservationGroupRequestDTO dto, UUID principalUuid, boolean isAdmin) {
        if (!isAdmin && !dto.userUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only create reservation groups for yourself");

        User user = userRepository.findByUuid(dto.userUuid())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + dto.userUuid()));

        Semester semester = semesterRepository.findById(dto.semesterId())
            .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + dto.semesterId()));

        ReservationGroup group = mapper.toEntity(dto);
        group.setUser(user);
        group.setSemester(semester);
        group.setStatus(ReservationGroupStatus.ACTIVE);

        return mapper.toDto(repository.save(group));
    }

    /**
     * Cancels an active reservation group.
     *
     * <p>Unless {@code isAdmin} is {@code true}, the {@code principalUuid} must match
     * the group owner to prevent a Maestro from cancelling another user's group
     * (BOLA/IDOR protection).</p>
     *
     * @param uuid          public UUID of the group to cancel
     * @param principalUuid public UUID of the authenticated user performing the cancellation
     * @param isAdmin       whether the authenticated user has the ADMIN role
     * @throws AccessDeniedException     when a non-admin tries to cancel another user's group
     * @throws ResourceNotFoundException when the group does not exist
     * @throws DomainException           when the group is already cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservationGroupResponseDTO cancel(UUID uuid, UUID principalUuid, boolean isAdmin) {
        ReservationGroup group = repository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation group not found: " + uuid));

        if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
            throw new AccessDeniedException("You can only cancel your own reservation groups");

        if (group.getStatus() == ReservationGroupStatus.CANCELLED)
            throw new DomainException("Reservation group is already cancelled");

        group.setStatus(ReservationGroupStatus.CANCELLED);
        return mapper.toDto(repository.save(group));
    }
}
