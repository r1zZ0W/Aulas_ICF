package mx.unam.icf.aulas.modules.reservations.groups.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;
import mx.unam.icf.aulas.kernel.infrastructure.persistance.UuidBinaryConverter;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a recurring classroom reservation group.
 *
 * <p>A group defines a weekly repetition pattern (days of week) for a specific user
 * within a given semester. Individual date occurrences are materialized as
 * {@link ReservInstance} records.</p>
 *
 * @author Ithera
 * @version 2.0
 * @see ReservInstance
 * @see ReservationGroupStatus
 */
@Entity
@Table(name = "reservation_groups")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ReservationGroup extends BaseEntity {

    /** Public UUID exposed through external APIs, stored as BINARY(16). */
    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /** User who owns this recurring reservation group. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Semester during which this reservation pattern is valid. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    /** Current lifecycle status of this group. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservationGroupStatus status;

    /**
     * Days of the week on which this reservation pattern repeats.
     * Backed by the {@code reservation_group_days} join table.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "reservation_group_days",
            joinColumns = @JoinColumn(name = "group_id")
    )
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek;

    /** Individual date-specific occurrences generated from this group. */
    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private List<ReservInstance> instances;
}
