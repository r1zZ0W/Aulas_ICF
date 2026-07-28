package mx.unam.icf.aulas.kernel.infrastructure.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Bootstraps the first {@code ADMIN} user on a freshly provisioned, empty database.
 *
 * <p>Runs once per application startup, after the context is fully initialized
 * ({@link ApplicationReadyEvent}). It is idempotent: it does nothing once at least one
 * {@code ADMIN} user already exists.</p>
 *
 * <p><b>Deployment prerequisite:</b> this seeder only creates the {@code users} row — it does
 * NOT create the schema or the {@code role} catalog. Flyway owns both: {@code V1__initial_schema.sql}
 * creates the tables and {@code R__reference_data.sql} guarantees the {@code ADMIN}/{@code TEACHER}
 * role rows exist, both applying automatically before the Spring context — and therefore this
 * listener — finishes starting up. No manual step is required.</p>
 *
 * <p><b>Fail-fast on an unusable fresh install:</b> if no {@code ADMIN} exists yet and seeding is
 * enabled but under-configured (missing credentials, or a password under
 * {@value #MIN_PASSWORD_LENGTH} characters), this method throws {@link IllegalStateException}
 * instead of merely logging. Letting the application start "successfully" with an empty
 * {@code users} table and no way to log in would leave a silent zombie deployment — systemd would
 * report the unit as {@code active} while the service is unusable by anyone. Throwing from an
 * {@link ApplicationReadyEvent} listener propagates out of {@code SpringApplication.run()}, closes
 * the context, and exits the JVM with a non-zero status, so systemd correctly reports {@code failed}.</p>
 *
 * <p><b>Role literal:</b> roles are stored by their bare name ({@code ADMIN}, not {@code ROLE_ADMIN}).
 * {@code UserDetailsImp} prepends the {@code ROLE_} prefix at runtime when building the Spring
 * Security {@link org.springframework.security.core.GrantedAuthority}, so seeding {@code ROLE_ADMIN}
 * here directly would produce {@code ROLE_ROLE_ADMIN} and make every {@code @PreAuthorize
 * hasRole('ADMIN')} check fail with 403.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder {

    private static final String ADMIN_ROLE_NAME = "ADMIN";

    /**
     * Minimum acceptable length for a bootstrap admin password. Below this, the seeder aborts
     * startup rather than provisioning a weak administrator account. 12 was chosen so the dev
     * default ({@code Admin@12345!}, 12 characters) still passes without a profile-specific
     * exception to the rule.
     */
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository   userRepository;
    private final RoleRepository   roleRepository;
    private final PasswordEncoder  passwordEncoder;

    /**
     * Explicit kill switch for the seeder. Must be checked before any credential validation:
     * it is the only supported way to start the application against an empty {@code users}
     * table without provisioning an admin (e.g. mid-restore from a backup that will bring its
     * own users). Defaults to enabled.
     */
    @Value("${app.seed.admin-enabled:true}")
    private boolean adminSeedEnabled;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.admin-email:}")
    private String adminEmail;

    @Value("${app.seed.admin-username:admin}")
    private String adminUsername;

    @Value("${app.seed.admin-first-name:Admin}")
    private String adminFirstName;

    @Value("${app.seed.admin-last-names:Sistema}")
    private String adminLastNames;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedAdminUser() {
        if (!adminSeedEnabled) {
            log.info("Admin bootstrap disabled via app.seed.admin-enabled=false — skipping.");
            return;
        }

        if (userRepository.existsByRole_Name(ADMIN_ROLE_NAME)) {
            log.debug("Admin bootstrap skipped: an ADMIN user already exists.");
            return;
        }

        // No ADMIN exists yet and seeding is enabled: this MUST succeed, or the deployment is
        // unusable — a database with tables but no way to log in. Abort startup instead of
        // letting the application report healthy while nobody can reach it.
        if (adminPassword == null || adminPassword.isBlank() || adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException(
                    "No ADMIN user exists and APP_SEED_ADMIN_PASSWORD/APP_SEED_ADMIN_EMAIL are not set. "
                    + "Set both environment variables and restart to bootstrap the first admin, or set "
                    + "APP_SEED_ADMIN_ENABLED=false if this startup is intentionally not meant to "
                    + "provision one (e.g. restoring from a backup).");
        }

        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "APP_SEED_ADMIN_PASSWORD is only " + adminPassword.length() + " characters long; "
                    + "the bootstrap admin password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters. Refusing to provision a weak administrator account.");
        }

        // Fail fast with a clear message rather than a bare Optional.empty()-related NPE or a
        // foreign-key violation: this is the expected failure mode only if Flyway's
        // R__reference_data.sql has not run yet, which should never happen in normal startup.
        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot bootstrap the admin user: role '" + ADMIN_ROLE_NAME + "' does not exist. "
                        + "R__reference_data.sql should have created it — check the Flyway migration history."));

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName(adminFirstName);
        admin.setLastNames(adminLastNames);
        admin.setRole(adminRole);
        admin.setInstitutionalId(generateMatricula());

        userRepository.save(admin);

        log.warn("=== SECURITY BOOTSTRAP === Initial ADMIN user created (username: {}). "
                + "Remove APP_SEED_ADMIN_PASSWORD / APP_SEED_ADMIN_EMAIL from the environment "
                + "now that the first login has been provisioned.", adminUsername);
    }

    /**
     * Generates a unique matrícula in the format {@code ICF<yyyy><5 digits>}, mirroring
     * {@code UserService.generateMatricula()}. Duplicated here (rather than reused) because that
     * method is package-private and this seeder intentionally lives outside the users module.
     */
    private String generateMatricula() {
        String year = String.valueOf(LocalDate.now().getYear());
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            String candidate = "ICF" + year + String.format("%05d", rng.nextInt(100_000));
            if (userRepository.findByInstitutionalId(candidate).isEmpty())
                return candidate;
        }
        throw new IllegalStateException("Could not generate a unique matricula for the bootstrap admin");
    }
}
