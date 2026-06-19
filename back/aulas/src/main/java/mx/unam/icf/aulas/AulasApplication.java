package mx.unam.icf.aulas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Aulas ICF API.
 *
 * <p>Bootstraps the application context, which includes auto-configuration for
 * Spring Security, Spring Data JPA, Spring Mail, and the REST controllers
 * defined across the {@code modules} package hierarchy.</p>
 */
@SpringBootApplication
public class AulasApplication {
    public static void main(String[] args) {
        SpringApplication.run(AulasApplication.class, args);
    }
}
