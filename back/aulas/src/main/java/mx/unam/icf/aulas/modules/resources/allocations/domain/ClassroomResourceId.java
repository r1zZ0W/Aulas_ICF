package mx.unam.icf.aulas.modules.resources.allocations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Composite primary key for the {@link ClassroomResource} join table.
 *
 * Implements {@link Serializable} as required by the JPA specification
 * for all embeddable identifier classes.
 *
 * @author Ithera
 * @version 1.0
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class ClassroomResourceId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** FK column referencing {@code classrooms.id}. */
    @Column(name = "classroom_id")
    private Long classroomId;

    /** FK column referencing {@code resources.id}. */
    @Column(name = "resource_id")
    private Integer resourceId;
}
