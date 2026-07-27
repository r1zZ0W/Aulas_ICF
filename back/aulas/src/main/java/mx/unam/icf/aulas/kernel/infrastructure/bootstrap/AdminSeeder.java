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
 * NOT create the schema or the {@code role} catalog. With {@code spring.jpa.hibernate.ddl-auto=validate}
 * (the production setting), Hibernate refuses to start against a database with no tables at all,
 * failing before this listener would ever run. The base schema and the {@code ADMIN}/{@code MAESTRO}
 * role rows must be applied manually first via {@code docs/migration_v1.0__baseline.sql}.</p>
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

    private final UserRepository   userRepository;
    private final RoleRepository   roleRepository;
    private final PasswordEncoder  passwordEncoder;

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
        if (userRepository.existsByRole_Name(ADMIN_ROLE_NAME)) {
            log.debug("Admin bootstrap skipped: an ADMIN user already exists.");
            return;
        }

        if (adminPassword == null || adminPassword.isBlank() || adminEmail == null || adminEmail.isBlank()) {
            // Noisy on purpose: with ddl-auto=validate the app can start cleanly against an
            // empty `users` table and give no other signal that nobody can log in.
            log.error("CRITICAL: no ADMIN user exists and APP_SEED_ADMIN_PASSWORD/APP_SEED_ADMIN_EMAIL "
                    + "are not set — no administrator was created. Nobody will be able to log in. "
                    + "Set both environment variables and restart to bootstrap the first admin.");
            return;
        }

        // Fail fast with a clear message rather than a bare Optional.empty()-related NPE or a
        // foreign-key violation: this is the expected failure mode if the baseline SQL script
        // (docs/migration_v1.0__baseline.sql) has not been applied yet.
        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot bootstrap the admin user: role '" + ADMIN_ROLE_NAME + "' does not exist. "
                        + "Apply docs/migration_v1.0__baseline.sql before starting the application."));

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
