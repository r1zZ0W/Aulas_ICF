package mx.unam.icf.aulas.modules.reservations.history.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.history.app.dtos.ReservationHistoryResponseDTO;
import mx.unam.icf.aulas.modules.reservations.history.app.mappers.ReservationHistoryMapper;
import mx.unam.icf.aulas.modules.reservations.history.domain.ReservationEvent;
import mx.unam.icf.aulas.modules.reservations.history.domain.ReservationHistory;
import mx.unam.icf.aulas.modules.reservations.history.infrastructure.ReservationHistoryRepository;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Application service for recording and querying reservation change history.
 *
 * <h3>Transaction semantics — Change History, not Security Audit</h3>
 * <p>All write methods use the default {@code REQUIRED} propagation, meaning they
 * participate in the <em>caller's</em> transaction. If the surrounding reservation
 * operation is rolled back (e.g. due to a constraint violation), the history row
 * is rolled back with it. This is intentional: only <em>successful</em> changes are
 * recorded. Recording <em>attempted/failed</em> actions (security telemetry) is a
 * separate concern that belongs in application logs (Logback / ELK) or a future
 * flat soft-reference audit table, outside this domain model.</p>
 *
 * <h3>Actor resolution</h3>
 * <p>{@link #resolveActor()} reads the Spring Security {@code ThreadLocal} context.
 * This works correctly for synchronous HTTP requests. Any {@code @Async},
 * {@code ThreadPoolTaskExecutor}, or {@code @Scheduled} job running on a different
 * thread will have an empty context and produce a {@code null} actor. In those cases,
 * callers <strong>must</strong> pass a descriptive {@code details} string (e.g.
 * {@code "System Cron: evicted expired reservation"}) since {@code details} becomes
 * the only trace of who or what acted.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class ReservationHistoryService {

    private final ReservationHistoryRepository repository;
    private final ReservationHistoryMapper     mapper;
    private final UserRepository               userRepository;

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Canonical writer — records a single audit event with full context.
     *
     * <p>If {@code performedBy} is {@code null} the actor is auto-resolved from
     * the current Spring Security context (HTTP request thread). Pass a non-null
     * {@code performedBy} only when the caller already has the {@link User} entity
     * in scope (e.g. the booking owner resolved during group validation).</p>
     *
     * @param group       the reservation group involved (nullable)
     * @param instance    the specific reservation instance involved (nullable)
     * @param event       the type of event to record
     * @param performedBy the user who triggered the event; {@code null} → context resolution
     * @param details     optional free-text note; required when the actor may be null
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(ReservationGroup group, ReservInstance instance,
                         ReservationEvent event, User performedBy, String details) {
        ReservationHistory record = new ReservationHistory();
        record.setGroup(group);
        record.setInstance(instance);
        record.setEventType(event);
        record.setPerformedBy(performedBy != null ? performedBy : resolveActor());
        record.setDetails(details);
        repository.save(record);
    }

    /**
     * Convenience writer — actor is always resolved from the security context.
     * Derives the group from {@code instance.getGroup()}.
     *
     * @param instance the reservation instance involved
     * @param event    the type of event to record
     * @param details  optional free-text note
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(ReservInstance instance, ReservationEvent event, String details) {
        register(instance.getGroup(), instance, event, null, details);
    }

    /**
     * Batch writer for recurring bookings.
     *
     * <p>Resolves the actor <em>once</em> and calls {@code saveAll} so a
     * semester-long recurrence (30–50 instances) collapses into a single JDBC
     * batch instead of N individual round-trips. Hibernate batching applies when
     * {@code spring.jpa.properties.hibernate.jdbc.batch_size} and
     * {@code rewriteBatchedStatements=true} are configured on the JDBC URL.</p>
     *
     * @param instances collection of instances to record (typically the result of
     *                  {@code repository.saveAll(toSave)} in the booking flow)
     * @param event     the type of event to record for each instance
     * @param details   optional free-text note applied to every row
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerAll(Collection<ReservInstance> instances, ReservationEvent event, String details) {
        User actor = resolveActor();
        List<ReservationHistory> records = instances.stream().map(instance -> {
            ReservationHistory record = new ReservationHistory();
            record.setGroup(instance.getGroup());
            record.setInstance(instance);
            record.setEventType(event);
            record.setPerformedBy(actor);
            record.setDetails(details);
            return record;
        }).toList();
        repository.saveAll(records);
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns the paginated history for a specific reservation instance.
     *
     * @param instanceUuid public UUID of the target instance
     * @param pageable     pagination and sort criteria (default: {@code createdAt DESC})
     * @return a {@link PagedResultDTO} of history records
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ReservationHistoryResponseDTO> findByInstance(UUID instanceUuid, Pageable pageable) {
        return PageMapper.toDto(repository.findByInstanceUuid(instanceUuid, pageable), mapper::toDtoList);
    }

    /**
     * Returns the paginated history for a reservation group (all its instances combined).
     *
     * @param groupUuid public UUID of the target group
     * @param pageable  pagination and sort criteria (default: {@code createdAt DESC})
     * @return a {@link PagedResultDTO} of history records
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<ReservationHistoryResponseDTO> findByGroup(UUID groupUuid, Pageable pageable) {
        return PageMapper.toDto(repository.findByGroupUuid(groupUuid, pageable), mapper::toDtoList);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the acting {@link User} from the current Spring Security context.
     *
     * <p>Returns {@code null} when there is no authenticated principal in scope
     * (e.g. system jobs, async threads). In those cases the caller must provide a
     * descriptive {@code details} string so the event remains interpretable.</p>
     *
     * @return the authenticated {@link User} proxy, or {@code null}
     */
    private User resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImp principal) {
            // getReferenceById creates a JPA proxy — no extra SELECT is needed to persist the FK
            return userRepository.getReferenceById(principal.getId());
        }
        return null;
    }
}
