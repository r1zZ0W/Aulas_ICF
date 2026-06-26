package mx.unam.icf.aulas.kernel.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Asynchronous execution configuration.
 *
 * <p>Defines a bounded {@link ThreadPoolTaskExecutor} used for best-effort
 * background tasks (e.g. email delivery). Without an explicit executor,
 * {@link EnableAsync} would fall back to {@code SimpleAsyncTaskExecutor}, which
 * creates an unbounded number of threads and can exhaust server resources under
 * moderate load.</p>
 *
 * <p>Sizing rationale (adjustable via {@code application.properties}):</p>
 * <ul>
 *   <li><b>corePoolSize 2</b> — keeps two threads alive for bursty email delivery.</li>
 *   <li><b>maxPoolSize 5</b> — allows short-lived spikes (e.g. bulk reassignments).</li>
 *   <li><b>queueCapacity 100</b> — buffers tasks before spawning extra threads;
 *       prevents immediate rejection under brief surges.</li>
 * </ul>
 *
 * @author Ithera
 * @version 1.0
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Task executor for {@code @Async} methods (primarily email sending).
     *
     * <p>The {@code mail-} thread-name prefix makes it easy to identify email
     * threads in logs and thread dumps.</p>
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }
}
