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

import java.util.UUID;

/**
 * Entity class representing a physical or logical resource available in classrooms
 * (e.g., projector, computer, air conditioning unit).
 *
 * This is a catalog entity. It does not extend {@code BaseEntity} (no audit
 * timestamps), but — like {@code Classroom} — exposes a public {@link #uuid}
 * so the API never leaks the internal {@link #id} to clients.
 *
 * @author Ithera
 * @version 2.0
 */
@Entity
@Table(name = "resources")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Resource {

    /** Auto-generated internal identifier. Never exposed through the API. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Public identifier used by every API request/response involving this resource. */
    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /** Unique resource name (e.g., Proyector Epson, Laptop Dell Latitude). */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** Optional human-readable description of the resource. */
    @Column(name = "description", length = 255)
    private String description;

    /** Total number of units of this equipment type in the global catalog. */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
