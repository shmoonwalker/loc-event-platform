package nl.loc.data.source.rvo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RvoClient {

    private final RestClient restClient;

    public RvoClient(RestClient.Builder builder,
                     @Value("${loc.rvo.base-url}") String baseUrl)
    {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

   public String fetchListPage(int page) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(String.class);
   }

   public String fetchEvent(String id) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{id}")
                        .build(id))
                .retrieve()
                .body(String.class);
   }
}
