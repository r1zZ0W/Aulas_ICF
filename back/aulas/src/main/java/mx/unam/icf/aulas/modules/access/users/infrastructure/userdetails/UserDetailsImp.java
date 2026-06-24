package mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails;

import lombok.Getter;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Adapts the {@link User} entity to the Spring Security {@link UserDetails} contract.
 *
 * <p>The public {@link UUID} is used as the user identifier throughout the API surface;
 * the internal numeric ID is intentionally kept private and never exposed to clients.
 * The granted authority is derived from the role name (e.g. {@code ROLE_MAESTRO}),
 * not from the numeric role ID, to keep authorization rules readable.</p>
 */
@Getter
public class UserDetailsImp implements UserDetails {

    /** Public-facing identifier used in API responses and JWT payload. */
    private final UUID uuid;

    /** Internal database ID — kept for JPA operations, never sent to clients. */
    private final Long id;

    /** Username used as the Spring Security username (login identifier). */
    private final String username;

    /** Email address stored for reference and API exposure. */
    private final String email;

    /** BCrypt-hashed password — never serialized or logged. */
    private final String password;

    /** Concatenation of firstName and lastNames, embedded in the JWT for display purposes. */
    private final String nombreCompleto;

    /** Role name (e.g. "MAESTRO", "ADMIN") used to build the {@link GrantedAuthority}. */
    private final String roleName;

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Builds the principal from a fully loaded {@link User} entity.
     * The role relationship must be initialized (not a lazy proxy) before calling this constructor.
     *
     * @param user the persisted user entity
     */
    public UserDetailsImp(User user) {
        this.uuid           = user.getUuid();
        this.id             = user.getId();
        this.username       = user.getUsername();
        this.email          = user.getEmail();
        this.password       = user.getPasswordHash();
        this.roleName       = user.getRole().getName();
        this.authorities    = Collections.singleton(new SimpleGrantedAuthority("ROLE_" + this.roleName));
        this.nombreCompleto = (user.getFirstName() + " " + user.getLastNames()).trim();
    }

    /** Returns the username as the Spring Security username. */
    @Override
    public @NonNull String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled()              { return true; }
}
