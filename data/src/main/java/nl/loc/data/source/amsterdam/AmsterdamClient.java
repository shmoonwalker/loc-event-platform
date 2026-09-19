package nl.loc.data.source.amsterdam;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AmsterdamClient {

    private final RestClient restClient;

    public AmsterdamClient(
            RestClient.Builder builder,
            @Value("${loc.amsterdam.base-url}") String baseUrl,
            @Value("${loc.amsterdam.api-key}") String apiKey) {

        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("Accept", "application/hal+json")
                .build();
    }

    public String fetchEvents() {
        return restClient.get()
                .uri("/evenementen/evenementen/")
                .retrieve()
                .body(String.class);
    }
}