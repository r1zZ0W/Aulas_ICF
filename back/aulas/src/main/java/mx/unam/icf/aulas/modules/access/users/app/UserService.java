package mx.unam.icf.aulas.modules.access.users.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.kernel.infrastructure.services.NotificationService;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import mx.unam.icf.aulas.modules.access.users.app.dtos.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserResponseDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserSelfEditRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserStatsDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserUpdateRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.mappers.UserMapper;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing the lifecycle of {@link User} entities.
 *
 * <p>Covers user registration (admin-only), profile retrieval, admin updates,
 * soft-deactivation, and self-service profile editing.</p>
 *
 * <p>On registration (DFR §3.1):</p>
 * <ul>
 *   <li>The role defaults to {@code MAESTRO} when {@code roleId} is omitted.</li>
 *   <li>A unique {@code matricula} is auto-generated in the format {@code ICF<yyyy><5 digits>}.</li>
 *   <li>An HTML email with credentials is sent to the new user's institutional address (best-effort).</li>
 * </ul>
 *
 * @author Ithera
 * @version 3.0
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final PasswordEncoder        passwordEncoder;
    private final UserMapper             userMapper;
    private final NotificationService    notificationService;

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a new user account. Restricted to ADMIN role.
     *
     * <p>When {@code roleId} is {@code null}, the system defaults the role to {@code MAESTRO} (DFR §3.1).
     * A unique matrícula is generated automatically. Credentials are sent by email after save.</p>
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

        Role role;
        if (request.roleId() == null) {
            // DFR §3.1: default role is MAESTRO
            role = roleRepository.findByName("MAESTRO")
                .orElseThrow(() -> new ResourceNotFoundException("Default role MAESTRO not found — ensure seed migration V4 has run"));
        } else {
            role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.roleId()));
        }

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastNames(request.lastNames());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setIsActive(true);
        user.setMatricula(generateMatricula());
        user.setDepartamento(request.departamento());

        userRepository.save(user);

        // DFR §3.1: send credentials email (best-effort; SMTP failures do not roll back).
        notificationService.notifyNewUserCredentials(
                user.getEmail(),
                user.getFirstName() + " " + user.getLastNames(),
                user.getMatricula(),
                user.getUsername(),
                user.getDepartamento(),
                request.password()
        );
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a page of users in the system, optionally filtered by a search term.
     *
     * <p>When {@code search} is {@code null} or blank the full catalog is returned
     * (backward-compatible with the no-search path). When provided, a case-insensitive
     * {@code LIKE} match is performed on {@code firstName}, {@code lastNames},
     * {@code email}, {@code username}, and {@code matricula}; {@code totalElements}
     * reflects the filtered count, so the frontend paginador stays correct.</p>
     *
     * @param search   optional free-text filter (trimmed by the repository query)
     * @param pageable pagination and sort criteria (validated by
     *                 {@link mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteriaArgumentResolver})
     * @return a {@link PagedResultDTO} containing the requested page of users
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<UserResponseDTO> findAll(String search, Pageable pageable) {
        Page<User> page = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.search(search.trim(), pageable);
        return PageMapper.toDto(page, userMapper::toDtoList);
    }

    /**
     * Returns aggregated user statistics for the admin dashboard.
     *
     * <p>Resolves all four counters (total, active, inactive, admins) in a single
     * database round-trip without materializing the full user list.</p>
     *
     * @return a {@link UserStatsDTO} with the current counts
     */
    @Transactional(readOnly = true)
    public UserStatsDTO getStats() {
        return userRepository.fetchStats();
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

    // ── Admin mutations ───────────────────────────────────────────────────────

    /**
     * Updates a user's profile information. Restricted to ADMIN role.
     *
     * <p>An administrator cannot modify their own role or active status via this method
     * to prevent accidental privilege loss (DFR §3.2).</p>
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
        user.setDepartamento(dto.departamento());
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

    // ── Self-service ──────────────────────────────────────────────────────────

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generates a unique matrícula in the format {@code ICF<yyyy><5 digits>} (e.g. {@code ICF202600001}).
     * Retries up to 10 times on collision; collision probability is negligible for admin-only registration volumes.
     *
     * @throws DomainException after 10 failed attempts (extremely unlikely)
     */
    String generateMatricula() {
        String year = String.valueOf(LocalDate.now().getYear());
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            String candidate = "ICF" + year + String.format("%05d", rng.nextInt(100_000));
            if (userRepository.findByMatricula(candidate).isEmpty())
                return candidate;
        }
        throw new DomainException("Could not generate a unique matricula; please try again");
    }
}
