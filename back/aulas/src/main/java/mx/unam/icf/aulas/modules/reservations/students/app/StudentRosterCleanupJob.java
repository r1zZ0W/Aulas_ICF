package mx.unam.icf.aulas.modules.reservations.students.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.kernel.app.StoredFile;
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.infrastructure.ReservSlotRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * System hygiene job — the storage⇄database consistency component of the roster design.
 * The filesystem is not transactional, so this job sweeps <b>both</b> directions of
 * possible divergence:
 *
 * <ol>
 *   <li><b>Groups without a roster file</b> ({@link #reapAbandonedRosters}): legacy groups
 *       created before the roster became mandatory at booking time whose owner never
 *       completed the upload. Deleting the group's {@code ReservSlot} rows frees the
 *       classroom; the {@code ReservInstance} and {@code ReservationGroup} rows are
 *       deleted with it. For bookings created by the atomic multipart flow this direction
 *       cannot occur — a storage failure there rolls the whole transaction back.</li>
 *   <li><b>Roster files without a group</b> ({@link #reapOrphanFiles}): the residue the
 *       atomic booking flow cannot prevent by ordering alone — {@code createBooking}
 *       writes the file as the transaction's last step, and if the database commit fails
 *       <em>after</em> the write succeeds, the rollback leaves the file on disk with no
 *       owning group. This sweep deletes those physical files.</li>
 * </ol>
 *
 * <p>Runs on a separate scheduler thread, so there is no authenticated
 * {@code SecurityContext} to resolve an acting user from (see
 * {@link mx.unam.icf.aulas.modules.reservations.history.app.ReservationHistoryService}'s
 * note on actor resolution) — this job intentionally does not write to the change-history
 * table; removals are logged instead.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentRosterCleanupJob {

    /** Groups older than this without a roster file are considered abandoned. */
    private static final Duration GRACE_PERIOD = Duration.ofDays(30);

    /**
     * Files younger than this are never treated as orphans. This age filter is
     * <b>load-bearing for correctness, not a courtesy</b>: the orphan sweep snapshots the
     * group UUIDs <em>before</em> iterating files, so a booking that commits between the
     * snapshot query and the file iteration produces a file whose group is missing from
     * the snapshot — skipping recent files is what prevents deleting that in-flight roster.
     */
    private static final Duration ORPHAN_FILE_MIN_AGE = Duration.ofHours(1);

    private final ReservationGroupRepository groupRepository;
    private final ReservInstanceRepository   reservInstanceRepository;
    private final ReservSlotRepository       slotRepository;
    private final FileStorageService          fileStorage;
    private final StudentListStorageProperties properties;

    /**
     * Runs every hour on the hour and removes groups created before the grace-period
     * cutoff that still have no roster file, along with their instances and slots.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(rollbackFor = Exception.class)
    public void reapAbandonedRosters() {
        LocalDateTime threshold = LocalDateTime.now().minus(GRACE_PERIOD);

        // Load candidate groups older than threshold and only reap those without a roster file
        List<ReservationGroup> candidates = groupRepository.findByCreatedAtBefore(threshold);
        if (candidates.isEmpty())
            return;

        int reaped = 0;
        for (ReservationGroup group : candidates) {
            try {
                // If a roster file exists, the group was already confirmed — skip it
                String filename = rosterFileName(group.getUuid());
                if (fileStorage.exists(properties.getStorageDir(), filename))
                    continue;

                reap(group);
                reaped++;
            } catch (Exception e) {
                // One failing group must not block the reaping of the rest.
                log.error("StudentRosterCleanupJob: failed to reap group {}", group.getUuid(), e);
            }
        }

        if (reaped > 0)
            log.info("StudentRosterCleanupJob: reaped {} abandoned group(s) created before {}", reaped, threshold);
    }

    /**
     * Inverse sweep: deletes roster files whose UUID does not correspond to any group in
     * the database (the residue of a booking whose file write succeeded but whose database
     * commit failed — see the class docs).
     *
     * <p>Design constraints honoured here:</p>
     * <ul>
     *   <li><b>One snapshot query, no N+1</b>: all group UUIDs are loaded once into a
     *       {@code Set} ({@link ReservationGroupRepository#findAllUuids}); each file is then
     *       checked with an in-memory {@code contains}, never a per-file database query.</li>
     *   <li><b>Lazy listing, caller-closed</b>: {@link FileStorageService#list} streams the
     *       folder one entry at a time; the try-with-resources here owns and closes the
     *       underlying directory handle.</li>
     *   <li><b>Race protection</b>: files newer than {@link #ORPHAN_FILE_MIN_AGE} are
     *       skipped (see that constant's docs). Filenames that do not parse as
     *       {@code <uuid>.xlsx} are skipped with a WARN — never deleted.</li>
     *   <li><b>Per-file fault isolation</b>: one unreadable/corrupt entry logs and moves
     *       on, mirroring {@link #reapAbandonedRosters}' loop.</li>
     * </ul>
     */
    @Scheduled(cron = "0 30 * * * *")
    public void reapOrphanFiles() {
        Set<UUID> knownGroupUuids = groupRepository.findAllUuids();
        Instant ageCutoff = Instant.now().minus(ORPHAN_FILE_MIN_AGE);

        int deleted = 0;
        try (Stream<StoredFile> files = fileStorage.list(properties.getStorageDir())) {
            for (StoredFile file : (Iterable<StoredFile>) files::iterator) {
                try {
                    if (file.lastModified().isAfter(ageCutoff))
                        continue; // too recent — may belong to an in-flight or post-snapshot booking

                    UUID uuid = parseRosterUuid(file.filename());
                    if (uuid == null) {
                        log.warn("StudentRosterCleanupJob: unexpected file in roster storage, skipping: {}",
                                file.filename());
                        continue;
                    }

                    if (knownGroupUuids.contains(uuid))
                        continue; // healthy file — its group exists

                    fileStorage.delete(properties.getStorageDir(), file.filename());
                    deleted++;
                    log.info("StudentRosterCleanupJob: deleted orphan roster file {} (no matching group)",
                            file.filename());
                } catch (Exception e) {
                    // One failing file must not block the sweeping of the rest.
                    log.error("StudentRosterCleanupJob: failed to process roster file {}", file.filename(), e);
                }
            }
        }

        if (deleted > 0)
            log.info("StudentRosterCleanupJob: deleted {} orphan roster file(s)", deleted);
    }

    /**
     * Extracts the group UUID from a {@code <uuid>.xlsx} roster filename, or {@code null}
     * when the name does not follow that pattern.
     */
    private UUID parseRosterUuid(String filename) {
        if (!filename.endsWith(".xlsx"))
            return null;
        try {
            return UUID.fromString(filename.substring(0, filename.length() - ".xlsx".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Deletes a single abandoned group's slots, instances, and the group itself, in
     * child-before-parent order.
     *
     * @param group the group (missing roster file) to remove
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

    private String rosterFileName(UUID groupUuid) {
        return groupUuid + ".xlsx";
    }
}
