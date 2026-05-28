package mx.unam.icf.aulas.modules.resources.classrooms.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;
import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResource;

import java.util.List;
import java.util.UUID;

/**
 * Entity class representing a classroom.
 *
 * This class encapsulates classroom information including its name, capacity,
 * public UUID, and optional linked classroom reference.
 *
 * It extends {@link BaseEntity} to inherit common persistence features.
 *
 * @author Ithera
 * @version 1.1
 * @see BaseEntity
 */
@Entity
@Table(name = "classrooms")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Classroom extends BaseEntity {

    /**
     * Public UUID used by external APIs.
     */
    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /**
     * The name of the classroom.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * The maximum capacity of students that can be accommodated in the classroom.
     */
    @Column(name = "capacity", nullable = false)
    private Long capacity;

    /**
     * Optional linked classroom entity for paired rooms.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_room_id")
    private Classroom linkedRoom;

    /**
     * Classroom resources associated with this classroom.
     */
    @OneToMany(mappedBy = "classroom", fetch = FetchType.LAZY)
    private List<ClassroomResource> classroomResources;

    /**
     * Flag that indicates if the classroom is active.
     */
    @Column(name = "is_active")
    private Boolean isActive;

}
