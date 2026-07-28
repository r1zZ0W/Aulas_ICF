package mx.unam.icf.aulas.modules.access.users.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.kernel.infrastructure.services.NotificationService;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteriaArgumentResolver;
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
import mx.unam.icf.aulas.modules.reservations.groups.infrastructure.ReservationGroupRepository;
import mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository;
import mx.unam.icf.aulas.modules.reservations.slots.infrastructure.ReservSlotRepository;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.app.mappers.PageMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service managing the lifecycle of {@link User} entities.
 *
 * <p>Covers user registration (admin-only), profile retrieval, admin updates,
 * hard deletion with cascade removal of all reservation data, and self-service profile editing.</p>
 *
 * <p>On registration (DFR §3.1):</p>
 * <ul>
 *   <li>The role defaults to {@code TEACHER} when {@code roleId} is omitted.</li>
 *   <li>A unique {@code matricula} is auto-generated in the format {@code ICF<yyyy><5 digits>}.</li>
 *   <li>An HTML email with credentials is sent to the new user's institutional address (best-effort).</li>
 * </ul>
 *
 * @author Ithera
 * @version 4.0
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository              userRepository;
    private final RoleRepository              roleRepository;
    private final PasswordEncoder             passwordEncoder;
    private final UserMapper                  userMapper;
    private final NotificationService         notificationService;
    private final ReservationGroupRepository  reservationGroupRepository;
    private final ReservInstanceRepository    reservInstanceRepository;
    private final ReservSlotRepository        reservSlotRepository;

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a new user account. Restricted to ADMIN role.
     *
     * <p>When {@code roleId} is {@code null}, the system defaults the role to {@code TEACHER} (DFR §3.1).
     * A unique matrícula is generated automatically. Credentials are sent by email after save.</p>
     *
     * @param request registration payload
     * @throws DomainException           when the email or username is already registered
     * @throws ResourceNotFoundException when the specified role does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(RegisterRequestDTO request) {
        if (userRepository.findByEmailIgnoreCase(request.email()).isPresent())
            throw new DomainException(ErrorCode.USER_EMAIL_TAKEN, "Email is already registered");

        if (userRepository.findByUsernameIgnoreCase(request.username()).isPresent())
            throw new DomainException(ErrorCode.USER_USERNAME_TAKEN, "Username is already registered");

        Role role;
        if (request.roleId() == null) {
            // DFR §3.1: default role is TEACHER
            role = roleRepository.findByName("TEACHER")
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Default role TEACHER not found — ensure R__reference_data.sql has run"));
        } else {
            role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found: " + request.roleId()));
        }

        User user = new User();

        if (request.firstName() != null)
            user.setFirstName(request.firstName());

        if (request.lastNames() != null)
            user.setLastNames(request.lastNames());

        if (request.username() != null)
            user.setUsername(request.username());

        if (request.email() != null)
            user.setEmail(request.email());

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);

        user.setInstitutionalId(generateMatricula());

        userRepository.save(user);

        // DFR §3.1: send credentials email (best-effort; SMTP failures do not roll back).
        notificationService.notifyNewUserCredentials(
                user.getEmail(),
                user.getFirstName() + " " + user.getLastNames(),
                user.getInstitutionalId(),
                user.getUsername(),
                request.password()
        );
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a page of users in the system, excluding the authenticated admin's own profile,
     * and optionally filtered by a search term.
     *
     * <p>The {@code currentUserUuid} exclusion is applied at the query level so that
     * {@code totalElements} stays consistent with the returned items and server-side
     * pagination remains correct. When {@code search} is {@code null} or blank the
     * full catalog (minus the current user) is returned. When provided, a case-insensitive
     * {@code LIKE} match is performed on {@code firstName}, {@code lastNames},
     * {@code email}, {@code username}, and {@code matricula}.</p>
     *
     * @param search          optional free-text filter (trimmed by the repository query)
     * @param pageable        pagination and sort criteria (validated by
     *                        {@link PageCriteriaArgumentResolver})
     * @param currentUserUuid public UUID of the authenticated admin; their row is excluded from results
     * @return a {@link PagedResultDTO} containing the requested page of users
     */
    @Transactional(readOnly = true)
    public PagedResultDTO<UserResponseDTO> findAll(String search, Pageable pageable, UUID currentUserUuid) {
        Page<User> page = (search == null || search.isBlank())
                ? userRepository.findAllExcluding(currentUserUuid, pageable)
                : userRepository.search(search.trim(), currentUserUuid, pageable);
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
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + uuid))
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
            throw new DomainException(ErrorCode.USER_SELF_ROLE_EDIT_FORBIDDEN, "Administrators cannot modify their own role or active status via this endpoint; use PUT /me for self-service edits");

        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + uuid));

        if (!user.getEmail().equalsIgnoreCase(dto.email())
                && userRepository.findByEmailIgnoreCase(dto.email()).isPresent())
            throw new DomainException(ErrorCode.USER_EMAIL_TAKEN, "Email is already registered: " + dto.email());

        if (!user.getUsername().equalsIgnoreCase(dto.username())
                && userRepository.findByUsernameIgnoreCase(dto.username()).isPresent())
            throw new DomainException(ErrorCode.USER_USERNAME_TAKEN, "Username is already registered: " + dto.username());


        if (dto.firstName() != null)
            user.setFirstName(dto.firstName());

        if (dto.lastNames() != null)
           user.setLastNames(dto.lastNames());

        if (dto.username() != null)
            user.setUsername(dto.username());

        if (dto.email() != null)
            user.setEmail(dto.email());

        if (dto.password() != null && !dto.password().isBlank())
            user.setPasswordHash(passwordEncoder.encode(dto.password()));

        Role role = roleRepository.findById(dto.roleId())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found: " + dto.roleId()));

        user.setRole(role);

        return userMapper.toDto(userRepository.save(user));
    }

    /**
     * Permanently deletes a user account along with all their reservation data
     * (slots → instances → groups, in FK-safe order). This operation is irreversible.
     *
     * <p>Deletion order (child before parent to respect NOT NULL FK constraints):</p>
     * <ol>
     *   <li>{@code reserv_slots} — deleted via bulk JPQL on the denormalized {@code user_id}.</li>
     *   <li>{@code reserv_instances} — deleted via bulk JPQL scoped to the user's groups.</li>
     *   <li>{@code reservation_group_days} + {@code reservation_groups} — deleted by entity so
     *       Hibernate removes the element-collection rows before the group rows.</li>
     *   <li>{@code users} — the user row itself.</li>
     * </ol>
     *
     * <p>Restricted to ADMIN role; an admin cannot delete their own account
     * (defence-in-depth: the listing endpoint already hides their own profile).</p>
     *
     * @param uuid            public UUID of the user to delete
     * @param currentUserUuid public UUID of the authenticated admin performing the action
     * @throws DomainException           when the admin attempts to delete their own account
     * @throws ResourceNotFoundException when the target user does not exist
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID uuid, UUID currentUserUuid) {
        if (uuid.equals(currentUserUuid))
            throw new DomainException(ErrorCode.USER_CANNOT_DELETE_SELF, "You cannot delete your own account");

        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + uuid));

        // 1. Slots (references instance_id + denormalized user_id)
        reservSlotRepository.deleteAllByUserId(user.getId());

        // 2. Instances (reference group_id)
        reservInstanceRepository.deleteAllByOwnerId(user.getId());

        // 3. Groups + reservation_group_days element collection
        //    deleteAll(entities) triggers per-entity DELETE so Hibernate cleans the
        //    @ElementCollection rows first — a bulk JPQL DELETE would skip that and violate the FK.
        reservationGroupRepository.deleteAll(reservationGroupRepository.findByUserUuid(uuid));

        // 4. User row
        userRepository.delete(user);
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
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + uuid));

        if (dto.firstName() != null)
            user.setFirstName(dto.firstName());

        if (dto.lastNames() != null)
            user.setLastNames(dto.lastNames());

        if (dto.username() != null && !dto.username().isBlank()) {
            if (!user.getUsername().equalsIgnoreCase(dto.username())
                    && userRepository.findByUsernameIgnoreCase(dto.username()).isPresent())
                throw new DomainException(ErrorCode.USER_USERNAME_TAKEN, "Username is already registered: " + dto.username());
            user.setUsername(dto.username());
        }

        if (dto.email() != null && !dto.email().isBlank()) {
            if (!user.getEmail().equalsIgnoreCase(dto.email())
                    && userRepository.findByEmailIgnoreCase(dto.email()).isPresent())
                throw new DomainException(ErrorCode.USER_EMAIL_TAKEN, "Email is already registered: " + dto.email());
            user.setEmail(dto.email());
        }

        if (dto.extension() != null)
            user.setExtension(dto.extension().isBlank() ? null : dto.extension().trim());

        if (dto.password() != null && !dto.password().isBlank()) {
            if (!"ADMIN".equalsIgnoreCase(user.getRole().getName())) {
                throw new DomainException(ErrorCode.USER_PASSWORD_CHANGE_FORBIDDEN, "Only administrators can change passwords from this profile");
            }
            user.setPasswordHash(passwordEncoder.encode(dto.password()));
        }

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
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: " + uuid));
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
            if (userRepository.findByInstitutionalId(candidate).isEmpty())
                return candidate;
        }
        throw new DomainException(ErrorCode.USER_MATRICULA_GENERATION_FAILED, "Could not generate a unique matricula; please try again");
    }
}
