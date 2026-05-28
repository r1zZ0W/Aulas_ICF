package mx.unam.icf.aulas.modules.access.users.infrastructure;

import lombok.AllArgsConstructor;
import mx.unam.icf.aulas.modules.access.users.domain.User;
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
    public @NonNull UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return new UserDetailsImp(user);
    }
}
