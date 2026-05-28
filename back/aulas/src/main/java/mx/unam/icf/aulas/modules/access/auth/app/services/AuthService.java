package mx.unam.icf.aulas.modules.access.auth.app.services;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.kernel.infrastructure.jwt.JwtProvider;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginResponseDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.register.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserDetailsImp;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that handles the login flow.
 *
 * <p>Delegates credential verification to Spring Security's {@link AuthenticationManager}
 * and issues a signed JWT on success. Any authentication failure is normalized to a
 * {@link BadCredentialsException} so callers receive a consistent error regardless of
 * whether the account is disabled or the password is wrong.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider           jwtProvider;
    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;

    /**
     * Authenticates the user and returns a JWT alongside the basic session data.
     *
     * @param request DTO containing the user's email and password
     * @return a {@link LoginResponseDTO} with the signed token and user info
     * @throws BadCredentialsException if authentication fails for any reason
     */
    public LoginResponseDTO login(LoginRequestDTO request) {
        Authentication authentication = authenticate(request.email(), request.password());
        String token = jwtProvider.generateToken(authentication);

        UserDetailsImp principal = (UserDetailsImp) authentication.getPrincipal();

        if  (principal == null)
            throw new BadCredentialsException("Invalid user");

        return new LoginResponseDTO(
                token,
                principal.getUuid(),
                principal.getNombreCompleto(),
                principal.getRoleName(),
                principal.getUsername()
        );
    }

    /**
     * Registers a new user with the provided data.
     *
     * @param request DTO containing the user's profile, credentials and role
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequestDTO request) {
        if (userRepository.findByEmailIgnoreCase(request.email()).isPresent())
            throw new DomainException("Email is already registered");

        if (userRepository.findByUsernameIgnoreCase(request.username()).isPresent())
            throw new DomainException("Username is already registered");

        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.roleId()));

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastNames(request.lastNames());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setIsActive(true);

        userRepository.save(user);
    }

    /**
     * Wraps the {@link AuthenticationManager} call to normalize all authentication
     * failures into a single {@link BadCredentialsException}, preventing the caller
     * from having to handle multiple Spring Security exception types.
     *
     * @param email    the user's email address
     * @param password the plain-text password supplied by the client
     * @return the fully populated {@link Authentication} on success
     * @throws BadCredentialsException if credentials are invalid or the account is disabled
     */
    private Authentication authenticate(String email, String password) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (DisabledException e) {
            throw new BadCredentialsException("Account is disabled");
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }
}
