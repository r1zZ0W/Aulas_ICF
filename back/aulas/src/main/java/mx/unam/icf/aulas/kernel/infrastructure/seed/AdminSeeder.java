package mx.unam.icf.aulas.kernel.infrastructure.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.modules.access.roles.infrastructure.RoleRepository;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Idempotent bootstrap seeder that creates an initial ADMIN user when none exists.
 *
 * <p>Only runs when {@code app.seed.admin-password} is non-blank, so it is safely
 * inert in production environments where the property is not set. Flyway migrations
 * (V4) ensure the ADMIN role already exists before this runner fires.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository    userRepository;
    private final RoleRepository    roleRepository;
    private final PasswordEncoder   passwordEncoder;

    @Value("${app.seed.admin-email:}")
    private String adminEmail;

    @Value("${app.seed.admin-username:admin}")
    private String adminUsername;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.admin-first-name:Admin}")
    private String adminFirstName;

    @Value("${app.seed.admin-last-names:Sistema}")
    private String adminLastNames;

    @Value("${app.seed.admin-departamento:Administración}")
    private String adminDepartamento;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.debug("[AdminSeeder] app.seed.admin-password not set — skipping bootstrap admin creation.");
            return;
        }

        // Check any ADMIN user (active or not) to avoid creating duplicates when a previous admin
        // has been soft-deactivated.
        if (userRepository.existsByRole_Name("ADMIN")) {
            log.debug("[AdminSeeder] ADMIN user already exists — skipping.");
            return;
        }

        var adminRole = roleRepository.findByName("ADMIN").orElse(null);
        if (adminRole == null) {
            log.warn("[AdminSeeder] ADMIN role not found — Flyway migration V4 may not have run yet. Skipping.");
            return;
        }

        User admin = new User();
        admin.setFirstName(adminFirstName);
        admin.setLastNames(adminLastNames);
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail.isBlank() ? adminUsername + "@icf.unam.mx" : adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(adminRole);
        admin.setIsActive(true);
        admin.setMatricula(generateMatricula());
        admin.setDepartamento(adminDepartamento);

        userRepository.save(admin);
        log.info("[AdminSeeder] Bootstrap ADMIN user created: username='{}', matricula='{}'.",
                admin.getUsername(), admin.getMatricula());
    }

    private String generateMatricula() {
        String year = String.valueOf(LocalDate.now().getYear());
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            String candidate = "ICF" + year + String.format("%05d", rng.nextInt(100_000));
            if (userRepository.findByMatricula(candidate).isEmpty())
                return candidate;
        }
        // Extremely unlikely; use UUID fallback
        return "ICF" + year + String.format("%05d", rng.nextInt(100_000));
    }
}
