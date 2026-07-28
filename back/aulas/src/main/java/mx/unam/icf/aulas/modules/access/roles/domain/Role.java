package mx.unam.icf.aulas.modules.access.roles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;

/**
 * Entity class representing a system role.
 *
 * This catalog entity defines permissions through predefined role names
 * such as TEACHER and ADMIN.
 *
 * @author Ithera
 * @version 1.0
 */
@Entity
@Table(name = "roles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Role extends BaseEntity {

    /**
     * Unique role name.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Optional description of the role.
     */
    @Column(name = "description")
    private String description;
}

