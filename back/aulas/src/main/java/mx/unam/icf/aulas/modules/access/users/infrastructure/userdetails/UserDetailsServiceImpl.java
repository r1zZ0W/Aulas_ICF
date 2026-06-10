package mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails;

import lombok.AllArgsConstructor;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} implementation backed by JPA.
 *
 * <p>Bridges the access module's {@link UserRepository} with the authentication
 * mechanism by loading a {@link User} entity and wrapping it in a {@link UserDetailsImp}
 * principal. The lookup is case-insensitive to tolerate minor email formatting differences.</p>
 */
@Service
@AllArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by their email address for Spring Security's authentication process.
     *
     * @param email the login identifier supplied by the client
     * @return a populated {@link UserDetailsImp} principal
     * @throws UsernameNotFoundException if no active user exists with the given email
     */
    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new UserDetailsImp(user);
    }

    /**
     * Loads a user by their public UUID, used during JWT refresh token validation
     * when only the UUID claim (not the email) is available in the token.
     *
     * @param uuid the public UUID string from the JWT {@code sub} claim
     * @return a populated {@link UserDetailsImp} principal
     * @throws UsernameNotFoundException if no user with the given UUID exists
     */
    public UserDetails loadUserByUuid(String uuid) {
        User user = userRepository.findByUuid(java.util.UUID.fromString(uuid))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + uuid));
        return new UserDetailsImp(user);
    }
}
