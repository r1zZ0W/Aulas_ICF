package mx.unam.icf.aulas.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Authentication bean configuration for the Aulas ICF API.
 *
 * <p>Kept separate from {@code MainSecurity} to avoid a circular dependency:
 * {@code MainSecurity} injects the JWT filter, the filter injects services that depend on
 * {@code PasswordEncoder}, and {@code PasswordEncoder} would otherwise need to be defined
 * in the same config class that wires the filter chain.</p>
 */
@Configuration
@RequiredArgsConstructor
public class AuthConfig {

    /**
     * BCrypt password encoder with cost factor 12, used for hashing passwords at registration
     * and verifying them at login. Cost 12 provides a good balance between security and latency.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the {@link AuthenticationManager} configured by Spring Security so it can be
     * injected into service-layer components (e.g., {@code AuthService}) without triggering
     * a circular dependency.
     *
     * @param authenticationConfiguration auto-configured by Spring Security
     * @return the application's {@link AuthenticationManager}
     * @throws Exception if the underlying configuration fails to load
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}

