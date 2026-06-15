package mx.unam.icf.aulas.modules.access.users.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import mx.unam.icf.aulas.modules.access.users.app.dtos.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserResponseDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserSelfEditRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserUpdateRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.mappers.UserMapper;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing the lifecycle of {@link User} entities.
 *
 * <p>Covers user registration (admin-only), profile retrieval, admin updates,
 * soft-deactivation, and self-service profile editing.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Registers a new user account. Restricted to ADMIN role.
     *
     * @param request registration payload
     * @throws DomainException           when the email or username is already registered
     * @throws ResourceNotFoundException when the specified role does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(RegisterRequestDTO request) {
        if (userRepository.findByEmailIgnoreCase(request.email()).isPresent())
            throw new DomainException("Email is already registered");

        if (userRepository.findByUsernameIgnoreCase(request.username()).isPresent())
            throw new DomainException("Username is already registered");

        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.roleId()));

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
     * Returns all users in the system. Restricted to ADMIN role.
     * GET /api/v1/users
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userMapper.toDtoList(userRepository.findAll());
    }

    /**
     * Returns a single user by their public UUID. Restricted to ADMIN role.
     *
     * @param uuid public UUID of the user
     * @throws ResourceNotFoundException when no user matches the given UUID
     */
    @Transactional(readOnly = true)
    public UserResponseDTO findByUuid(UUID uuid) {
        return userMapper.toDto(
            userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uuid))
        );
    }

    /**
     * Updates a user's profile information. Restricted to ADMIN role.
     *
     * <p>An administrator cannot modify their own role or active status via this method
     * to prevent accidental privilege loss (DFR §3.2). For self-service profile edits
     * (username/password) use {@link #selfEdit(UUID, UserSelfEditRequestDTO)} instead.</p>
     *
     * @param uuid            public UUID of the user to update
     * @param dto             update payload
     * @param currentUserUuid public UUID of the authenticated admin performing the update
     * @throws DomainException           when the admin attempts to modify their own account
     * @throws ResourceNotFoundException when the user or role is not found
     * @throws DomainException           when the new email or username is already taken
     */
    @Transactional(rollbackFor = Exception.class)
    public UserResponseDTO update(UUID uuid, UserUpdateRequestDTO dto, UUID currentUserUuid) {
        if (uuid.equals(currentUserUuid))
            throw new DomainException("Administrators cannot modify their own role or active status via this endpoint; use PUT /me for self-service edits");

        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uuid));

        if (!user.getEmail().equalsIgnoreCase(dto.email())
                && userRepository.findByEmailIgnoreCase(dto.email()).isPresent())
            throw new DomainException("Email is already registered: " + dto.email());

        if (!user.getUsername().equalsIgnoreCase(dto.username())
                && userRepository.findByUsernameIgnoreCase(dto.username()).isPresent())
            throw new DomainException("Username is already registered: " + dto.username());

        Role role = roleRepository.findById(dto.roleId())
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + dto.roleId()));

        user.setFirstName(dto.firstName());
        user.setLastNames(dto.lastNames());
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setRole(role);
        if (dto.isActive() != null)
            user.setIsActive(dto.isActive());

        return userMapper.toDto(userRepository.save(user));
    }

    /**
     * Soft-deactivates a user account, preserving reservation history.
     * Restricted to ADMIN role; an admin cannot deactivate their own account.
     *
     * @param uuid            public UUID of the user to deactivate
     * @param currentUserUuid public UUID of the authenticated admin performing the action
     * @throws DomainException           when the admin attempts to deactivate their own account
     * @throws ResourceNotFoundException when the target user does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public void deactivate(UUID uuid, UUID currentUserUuid) {
        if (uuid.equals(currentUserUuid))
            throw new DomainException("You cannot deactivate your own account");

        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uuid));

        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Allows an authenticated user to update their own username and/or password.
     *
     * @param uuid public UUID of the authenticated user
     * @param dto  self-edit payload (both fields are optional)
     * @throws ResourceNotFoundException when the user does not exist
     * @throws DomainException           when the requested username is already taken
     */
    @Transactional(rollbackFor = Exception.class)
    public UserResponseDTO selfEdit(UUID uuid, UserSelfEditRequestDTO dto) {
        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uuid));

        if (dto.username() != null && !dto.username().isBlank()) {
            if (!user.getUsername().equalsIgnoreCase(dto.username())
                    && userRepository.findByUsernameIgnoreCase(dto.username()).isPresent())
                throw new DomainException("Username is already registered: " + dto.username());
            user.setUsername(dto.username());
        }

        if (dto.password() != null && !dto.password().isBlank())
            user.setPasswordHash(passwordEncoder.encode(dto.password()));

        return userMapper.toDto(userRepository.save(user));
    }

    /**
     * Updates a user's password by their internal UUID.
     * Used internally by the password-reset flow.
     *
     * @param uuid        public UUID of the user
     * @param rawPassword new plaintext password to hash and store
     * @throws ResourceNotFoundException when the user does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(UUID uuid, String rawPassword) {
        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uuid));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }
}
