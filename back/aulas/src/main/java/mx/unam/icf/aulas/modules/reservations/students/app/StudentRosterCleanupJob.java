package mx.unam.icf.aulas.modules.reservations.students.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.infrastructure.ReservSlotRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * System hygiene job: reaps {@link ReservationGroupStatus#PENDING_ROSTER} groups whose
 * mandatory student roster was never uploaded within the grace period.
 *
 * <p>Without this job, a reservation whose owner creates the booking (occupying the
 * classroom immediately, per {@code ReservInstanceService.createBooking}) but never
 * completes the roster upload would hold that classroom hostage forever. Deleting the
 * group's {@code ReservSlot} rows frees the classroom for new bookings; the
 * {@code ReservInstance} and {@code ReservationGroup} rows are deleted with it, since a
 * group without a roster was never confirmed.</p>
 *
 * <p>Runs on a separate scheduler thread, so there is no authenticated
 * {@code SecurityContext} to resolve an acting user from (see
 * {@link mx.unam.icf.aulas.modules.reservations.history.app.ReservationHistoryService}'s
 * note on actor resolution) — this job intentionally does not write to the change-history
 * table; the removal is logged instead.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentRosterCleanupJob {

    /** Groups older than this, still PENDING_ROSTER, are considered abandoned. */
    private static final Duration GRACE_PERIOD = Duration.ofHours(1);

    private final ReservationGroupRepository groupRepository;
    private final ReservInstanceRepository   reservInstanceRepository;
    private final ReservSlotRepository       slotRepository;

    /**
     * Runs every hour on the hour and removes every {@code PENDING_ROSTER} group created
     * before the grace-period cutoff, along with its instances and slots.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(rollbackFor = Exception.class)
    public void reapAbandonedRosters() {
        LocalDateTime threshold = LocalDateTime.now().minus(GRACE_PERIOD);
        List<ReservationGroup> abandoned =
                groupRepository.findByStatusAndCreatedAtBefore(ReservationGroupStatus.PENDING_ROSTER, threshold);

        if (abandoned.isEmpty())
            return;

        log.info("StudentRosterCleanupJob: reaping {} abandoned PENDING_ROSTER group(s) created before {}",
                abandoned.size(), threshold);

        for (ReservationGroup group : abandoned) {
            try {
                reap(group);
            } catch (Exception e) {
                // One failing group must not block the reaping of the rest.
                log.error("StudentRosterCleanupJob: failed to reap group {}", group.getUuid(), e);
            }
        }
    }

    /**
     * Deletes a single abandoned group's slots, instances, and the group itself, in
     * child-before-parent order.
     *
     * @param group the {@code PENDING_ROSTER} group to remove
     */
    @Transactional(rollbackFor = Exception.class)
    public void reap(ReservationGroup group) {
        Long groupId = group.getId();
        slotRepository.deleteAllByGroupId(groupId);
        reservInstanceRepository.deleteAllByGroupId(groupId);
        groupRepository.delete(group);
        log.info("StudentRosterCleanupJob: removed abandoned reservation group {} (created {})",
                group.getUuid(), group.getCreatedAt());
    }
}
