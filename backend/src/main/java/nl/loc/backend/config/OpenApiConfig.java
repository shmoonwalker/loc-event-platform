package nl.loc.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI backendOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Loc Backend API")
                .description("Product API for qualified Loc events and user features.")
                .version("0.1.0"))
                // Relative to the page's own origin: behind the frontend proxy the backend only sees Host "backend".
                .servers(List.of(new Server().url("/")));
    }

    @Bean
    public OpenApiCustomizer sortPathsAlphabetically() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Paths sorted = new Paths();
            openApi.getPaths().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.addPathItem(entry.getKey(), entry.getValue()));
            openApi.setPaths(sorted);
        };
    }

    /**
     * The Scalar starter registers a ForwardedHeaderFilter unless this bean exists. That filter trusts the
     * left-most X-Forwarded-For entry, which the client controls, so anyone could fake their IP and bypass
     * rate limits. Tomcat resolves the client IP instead (server.forward-headers-strategy: native).
     */
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registration.setEnabled(false);
        return registration;
    }
}
