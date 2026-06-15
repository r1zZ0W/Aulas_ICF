package mx.unam.icf.aulas.security;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security filter-chain configuration for the Aulas ICF API.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>CSRF disabled — the API is stateless and uses JWT bearer tokens.</li>
 *   <li>CORS configured from {@code app.cors.allowed-origins} (defaults to {@code http://localhost:5173}).</li>
 *   <li>Session management set to {@code STATELESS} — no server-side session is created.</li>
 *   <li>Security headers: XSS protection enabled in block mode; CSP restricts scripts to {@code 'self'}.</li>
 *   <li>Public routes: only {@code /api/v1/auth/**} is accessible without authentication.</li>
 *   <li>JWT filter inserted before Spring's {@code UsernamePasswordAuthenticationFilter}.</li>
 *   <li>Method-level security enabled via {@code @EnableMethodSecurity} ({@code @PreAuthorize}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class MainSecurity {

    private final JwtAuthenticationFilter     jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler      accessDeniedHandler;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /** Injected to check the active Spring profile for Swagger gating. */
    @Autowired
    private Environment env;

    @Bean
    public SecurityFilterChain filterInternal(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsRegistry()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self'; object-src 'none';"))
                        .contentTypeOptions(Customizer.withDefaults())
            )
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**").permitAll();
                // Swagger/OpenAPI is only publicly accessible in the dev profile.
                // In production the springdoc properties disable the endpoints entirely;
                // this rule adds defence-in-depth at the security layer.
                if (env.acceptsProfiles(Profiles.of("dev"))) {
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            // Wire JSON error responses for the two security-layer rejection paths:
            //   401 → no token / unauthenticated (AuthenticationEntryPoint)
            //   403 → authenticated but insufficient role at URL level (AccessDeniedHandler)
            // @PreAuthorize 403s are handled separately by GlobalExceptionHandler.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsRegistry() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", config);
        return src;
    }
}
