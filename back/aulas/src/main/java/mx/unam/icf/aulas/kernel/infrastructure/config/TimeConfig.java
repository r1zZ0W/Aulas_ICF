package mx.unam.icf.aulas.kernel.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Application-wide time source.
 *
 * <p>Every business rule that asks "has this already happened?" must resolve <em>now</em>
 * through this {@link Clock}, never through a bare {@code LocalDate.now()}. Two reasons:</p>
 * <ul>
 *   <li><b>Timezone correctness</b> — a server running in UTC would consider a Mexican
 *       reservation finished up to six hours early (and would roll over to the next
 *       calendar day at 18:00 local time). The zone is pinned to {@code app.timezone}
 *       instead of inheriting the JVM default, which differs between a developer laptop
 *       and the deployment host.</li>
 *   <li><b>Testability</b> — unit tests inject {@code Clock.fixed(...)} to pin "today" and
 *       assert the exact boundary behaviour of a rule without sleeping or mocking statics.</li>
 * </ul>
 *
 * @author Ithera
 * @version 1.0
 */
@Configuration
public class TimeConfig {

    /**
     * System clock pinned to the application's business timezone.
     *
     * @param zone IANA timezone id from {@code app.timezone} (defaults to America/Mexico_City)
     */
    @Bean
    public Clock clock(@Value("${app.timezone:America/Mexico_City}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
