package mx.unam.icf.aulas.kernel.infrastructure.web;

import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteriaArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Global Spring MVC configuration for the kernel layer.
 *
 * <p>Registers the {@link PageCriteriaArgumentResolver} so that any controller
 * method with a {@link mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria}
 * parameter annotated with
 * {@link mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist}
 * has its pagination criteria resolved, validated, and injected automatically.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PageCriteriaArgumentResolver());
    }
}
