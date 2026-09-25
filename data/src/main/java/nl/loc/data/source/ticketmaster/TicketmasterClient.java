package nl.loc.data.source.ticketmaster;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class TicketmasterClient {

    private static final DateTimeFormatter API_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final String apiKey;
    private final String countryCode;
    private final String locale;
    private final int pageSize;

    public TicketmasterClient(RestClient.Builder builder,
                              @Value("${loc.ticketmaster.base-url}") String baseUrl,
                              @Value("${loc.ticketmaster.api-key}") String apiKey,
                              @Value("${loc.ticketmaster.country-code}") String countryCode,
                              @Value("${loc.ticketmaster.locale}") String locale,
                              @Value("${loc.ticketmaster.page-size}") int pageSize)
    {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.countryCode = countryCode;
        this.locale = locale;
        this.pageSize = pageSize;
    }

    public String fetchEventsPage(Instant startDateTime, Instant endDateTime, int page) {
        requireApiKey();
        log.debug("Fetching Ticketmaster events page={} startDateTime={} endDateTime={}",
                page, format(startDateTime), format(endDateTime));
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("apikey", apiKey)
                        .queryParam("countryCode", countryCode)
                        .queryParam("locale", locale)
                        .queryParam("sort", "date,asc")
                        .queryParam("size", pageSize)
                        .queryParam("page", page)
                        .queryParam("startDateTime", format(startDateTime))
                        .queryParam("endDateTime", format(endDateTime))
                        .queryParam("includeTBA", "yes")
                        .queryParam("includeTBD", "yes")
                        .build())
                .retrieve()
                .body(String.class);
    }

    public int pageSize() {
        return pageSize;
    }

    private void requireApiKey() {
        Assert.hasText(apiKey, "loc.ticketmaster.api-key is blank; set the Ticketmaster environment variable");
    }

    private static String format(Instant instant) {
        return API_INSTANT.format(instant);
    }
}
