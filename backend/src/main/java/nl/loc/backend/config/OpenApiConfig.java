package nl.loc.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI backendOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Loc Backend API")
                .description("Product API for qualified Loc events and user features.")
                .version("0.1.0"));
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
}
