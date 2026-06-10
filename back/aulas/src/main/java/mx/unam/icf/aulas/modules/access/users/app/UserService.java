package mx.unam.icf.aulas.modules.access.users.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import mx.unam.icf.aulas.modules.access.users.app.dtos.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserRequestDTO;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for user lifecycle operations.
 *
 * <p>Handles new-user registration (enforcing email and username uniqueness,
 * BCrypt-hashing the password, and assigning the requested role) and
 * password updates triggered by the password-reset flow.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;

    /**
     * Registers a new user with the provided data.
     *
     * @param request DTO containing the user's profile, credentials and role
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(RegisterRequestDTO request) {
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
     * Updates the BCrypt-hashed password for the user identified by the given UUID.
     * Called exclusively by the password-reset flow after the reset token has been validated.
     *
     * @param uuid        public UUID of the user whose password is being changed
     * @param rawPassword the new plain-text password (will be encoded before persistence)
     * @throws ResourceNotFoundException if no user with the given UUID exists
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(java.util.UUID uuid, String rawPassword) {
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }
}
