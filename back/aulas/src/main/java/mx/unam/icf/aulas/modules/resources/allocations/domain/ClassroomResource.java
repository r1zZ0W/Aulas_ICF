package mx.unam.icf.aulas.modules.resources.allocations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;

/**
 * Entity representing the many-to-many association between classrooms and equipment,
 * enriched with the {@code quantity} attribute.
 *
 * Uses {@link ClassroomResourceId} as a composite primary key via {@code @EmbeddedId},
 * with {@code @MapsId} linking each FK field to its corresponding entity relationship.
 *
 * @author Ithera
 * @version 1.0
 * @see Classroom
 * @see Resource
 */
@Entity
@Table(name = "classroom_resources")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ClassroomResource {

    /** Composite primary key composed of classroomId and resourceId. */
    @EmbeddedId
    private ClassroomResourceId id;

    /** The classroom that owns this equipment allocation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("classroomId")
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    /** The piece of equipment allocated to the classroom. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("resourceId")
    @JoinColumn(name = "resource_id")
    private Resource resource;

    /** Number of units of this equipment available in the classroom. */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
