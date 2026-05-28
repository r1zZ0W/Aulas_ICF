package mx.unam.icf.aulas.modules.resources.equipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity class representing a physical or logical resource available in classrooms
 * (e.g., projector, computer, air conditioning unit).
 *
 * This is a pre-seeded catalog entity. It does not extend {@code BaseEntity}
 * because it carries no UUID or audit timestamps.
 *
 * @author Ithera
 * @version 1.0
 */
@Entity
@Table(name = "resources")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Resource {

    /** Auto-generated internal identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Unique resource name (e.g., PROYECTOR, COMPUTADORA, AIRE_ACONDICIONADO). */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** Optional human-readable description of the resource. */
    @Column(name = "description", length = 255)
    private String description;
}
