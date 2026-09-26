package nl.loc.data.source.ticketmaster;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class TicketmasterClient {

    private static final DateTimeFormatter API_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final long REQUEST_INTERVAL_MS = 250;
    private static final int MAX_ATTEMPTS = 4;
    private static final long MAX_RETRY_DELAY_MS = 60_000;

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

    public synchronized String fetchEventsPage(Instant startDateTime, Instant endDateTime, int page) {
        requireApiKey();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            pause(REQUEST_INTERVAL_MS);
            try {
                return requestEventsPage(startDateTime, endDateTime, page);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status == 500 || status == 502
                        || status == 503 || status == 504;
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                long delayMs = retryDelayMs(exception, attempt);
                if (delayMs > MAX_RETRY_DELAY_MS) {

                    log.warn("Ticketmaster retry deferred page={} status={} requestedDelayMs={} exceedsMaxDelayMs={}",
                            page, status, delayMs, MAX_RETRY_DELAY_MS);
                    throw exception;
                }
                log.warn("Retrying Ticketmaster page={} status={} nextAttempt={} delayMs={}",
                        page, status, attempt + 1, delayMs);
                pause(delayMs);
            }
        }
        throw new IllegalStateException("Ticketmaster request attempts exhausted");
    }

    private String requestEventsPage(Instant startDateTime, Instant endDateTime, int page) {
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

    private static long retryDelayMs(RestClientResponseException exception, int attempt) {
        long backoffMs = 1_000L << (attempt - 1);
        String retryAfter = exception.getResponseHeaders() == null
                ? null : exception.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return backoffMs;
        }
        retryAfter = retryAfter.strip();
        try {
            long seconds = Long.parseLong(retryAfter);
            if (seconds > MAX_RETRY_DELAY_MS / 1_000) {
                return MAX_RETRY_DELAY_MS + 1;
            }
            return Math.max(backoffMs, Math.max(0, seconds) * 1_000);
        } catch (NumberFormatException exceptionIgnored) {
            try {
                Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration wait = Duration.between(Instant.now(), retryAt);
                if (wait.compareTo(Duration.ofMillis(MAX_RETRY_DELAY_MS)) > 0) {
                    return MAX_RETRY_DELAY_MS + 1;
                }
                return wait.isNegative() ? backoffMs : Math.max(backoffMs, wait.toMillis());
            } catch (DateTimeParseException invalidHeader) {
                return backoffMs;
            }
        }
    }

    private static void pause(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ticketmaster collection interrupted while waiting to request", exception);
        }
    }

    public int pageSize() {
        return pageSize;
    }

    public String countryCode() {
        return countryCode;
    }

    private void requireApiKey() {
        Assert.hasText(apiKey, "loc.ticketmaster.api-key is blank; set the Ticketmaster environment variable");
    }

    private static String format(Instant instant) {
        return API_INSTANT.format(instant);
    }
}
