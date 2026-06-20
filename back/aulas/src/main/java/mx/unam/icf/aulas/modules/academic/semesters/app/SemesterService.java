package mx.unam.icf.aulas.modules.academic.semesters.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterRequestDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterResponseDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.mappers.SemesterMapper;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing the academic {@link Semester} catalog.
 *
 * <p>Semesters define the academic periods during which classroom reservations are active.
 * A semester is considered <em>current</em> when today's date falls within its inclusive
 * {@code [startDate, endDate]} range. Only one semester is operationally active at a time:
 * if overlapping ranges exist in the data, the one with the latest {@code endDate} wins.</p>
 *
 * <p>The service is the <strong>sole source of the reference date</strong> ({@code LocalDate.now()}).
 * It passes this value to the mapper so that neither the entity nor the mapper are coupled to
 * the system clock, keeping date logic trivially unit-testable with fixed dates.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository repository;
    private final SemesterMapper mapper;

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all semesters in the catalog with their derived {@code isActive} status.
     *
     * @return list of all semesters, each with {@code isActive} computed against today
     */
    @Transactional(readOnly = true)
    public List<SemesterResponseDTO> findAll() {
        LocalDate today = LocalDate.now();
        return mapper.toDtoList(repository.findAll(), today);
    }

    /**
     * Returns the single operationally active semester — the one whose
     * {@code [startDate, endDate]} range contains today.
     *
     * <p>If multiple overlapping semesters exist (data anomaly), the one with the
     * latest {@code endDate} is returned to produce a deterministic result.</p>
     *
     * @return the current semester
     * @throws ResourceNotFoundException when no semester covers the current date
     */
    @Transactional(readOnly = true)
    public SemesterResponseDTO findActive() {
        LocalDate today = LocalDate.now();
        Semester current = repository.findCurrent(today)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active semester for the current date: " + today));
        return mapper.toDto(current, today);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Creates a new semester. Requires ADMIN role.
     *
     * <p>On creation neither {@code startDate} nor {@code endDate} may be in the past,
     * and {@code endDate} must be strictly after {@code startDate}.</p>
     *
     * @param dto creation payload
     * @return the persisted semester with its derived {@code isActive} status
     * @throws DomainException when the name is already taken or the date rules are violated
     */
    @Transactional(rollbackFor = Exception.class)
    public SemesterResponseDTO save(SemesterRequestDTO dto) {
        LocalDate today = LocalDate.now();

        if (repository.findByName(dto.name()).isPresent())
            throw new DomainException("A semester with that name already exists: " + dto.name());

        validateDates(dto.startDate(), dto.endDate(), true, null, today);

        Semester semester = mapper.toEntity(dto);
        return mapper.toDto(repository.save(semester), today);
    }

    /**
     * Updates an existing semester. Requires ADMIN role.
     *
     * <p>The no-past rule for {@code endDate} is only enforced for semesters that are still
     * ongoing or scheduled in the future. A semester that has already concluded may have its
     * metadata (including dates) freely edited as long as {@code endDate > startDate} holds.
     * This allows administrators to correct historical records without being blocked.</p>
     *
     * @param uuid external identifier of the semester to update
     * @param dto  update payload
     * @return the updated semester with its derived {@code isActive} status
     * @throws ResourceNotFoundException when no semester matches the given UUID
     * @throws DomainException           when the name is already taken or the date rules are violated
     */
    @Transactional(rollbackFor = Exception.class)
    public SemesterResponseDTO update(UUID uuid, SemesterRequestDTO dto) {
        LocalDate today = LocalDate.now();

        Semester semester = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + uuid));

        if (!semester.getName().equals(dto.name()) && repository.findByName(dto.name()).isPresent())
            throw new DomainException("A semester with that name already exists: " + dto.name());

        validateDates(dto.startDate(), dto.endDate(), false, semester.getEndDate(), today);

        mapper.updateEntityFromDto(dto, semester);
        return mapper.toDto(repository.save(semester), today);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Validates chronological coherence and the no-past rule for semester dates.
     *
     * <p>Rules applied in all cases:</p>
     * <ul>
     *   <li>{@code endDate} must be strictly after {@code startDate}.</li>
     * </ul>
     *
     * <p>Additional rules on <strong>create</strong>:</p>
     * <ul>
     *   <li>{@code startDate} must not be before {@code today}.</li>
     *   <li>{@code endDate} must not be before {@code today}.</li>
     * </ul>
     *
     * <p>Additional rules on <strong>update</strong> of an ongoing or future semester
     * (i.e., when the persisted {@code currentEnd >= today}):</p>
     * <ul>
     *   <li>The new {@code endDate} must not be set to a date before {@code today}.</li>
     *   <li>{@code startDate} is freely editable (allows correcting a start date that
     *       has already passed without blocking legitimate edits).</li>
     * </ul>
     *
     * <p>No extra rules are enforced when updating an <strong>already concluded</strong>
     * semester ({@code currentEnd < today}), beyond the basic {@code endDate > startDate}
     * invariant, so administrators can correct historical records.</p>
     *
     * @param start      requested start date
     * @param end        requested end date
     * @param isCreate   {@code true} for creation, {@code false} for update
     * @param currentEnd the persisted end date of the semester being updated ({@code null} on create)
     * @param today      reference date supplied by the caller (no system-clock coupling)
     * @throws DomainException when any rule is violated
     */
    private void validateDates(LocalDate start, LocalDate end,
                               boolean isCreate, LocalDate currentEnd, LocalDate today) {
        if (!end.isAfter(start))
            throw new DomainException("End date must be strictly after start date");

        if (isCreate) {
            if (start.isBefore(today))
                throw new DomainException("Start date cannot be in the past");
            if (end.isBefore(today))
                throw new DomainException("End date cannot be in the past");
            return;
        }

        // Update path: only enforce the no-past end-date rule for semesters that are
        // still ongoing or future. Already-concluded semesters may be freely edited.
        boolean alreadyConcluded = currentEnd != null && currentEnd.isBefore(today);
        if (!alreadyConcluded && end.isBefore(today))
            throw new DomainException("End date cannot be set to a past date for an ongoing or future semester");
    }
}
