package mx.unam.icf.aulas.modules.access.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;
import mx.unam.icf.aulas.kernel.infrastructure.persistance.UuidBinaryConverter;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;

import java.util.UUID;

/**
 * Entity class representing a system user (teacher or administrator).
 *
 * Extends {@link BaseEntity} to inherit the auto-generated id and audit timestamps.
 * The public-facing UUID is stored as BINARY(16) via {@link UuidBinaryConverter}.
 *
 * @author Ithera
 * @version 1.0
 * @see Role
 */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class User extends BaseEntity {

    /** Public UUID exposed through external APIs, stored as BINARY(16). */
    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /** Role assigned to this user, determining their access level. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** User's first name. */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /** User's last names (paternal and maternal). */
    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    /**User's username for being authenticated*/
    @Column(name = "username", nullable = false, length = 100, unique = true)
    private String username;

    /** Unique email address used as the login identifier. */
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** BCrypt hash of the user's password. Never exposed in API responses. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Soft-delete flag; inactive users cannot authenticate. */
    @Column(name = "is_active")
    private Boolean isActive;
}
