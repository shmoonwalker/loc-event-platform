package nl.loc.data.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads one hour of forecast from Open-Meteo.
 *
 * The request asks for a single hour rather than the full sixteen days, and works entirely in
 * UTC so the slot's start instant can be used as-is with no timezone conversion in between.
 */
@Slf4j
@Component
public class OpenMeteoClient {

    private static final DateTimeFormatter REQUEST_HOUR =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC);
    private static final String HOURLY_FIELDS =
            "temperature_2m,precipitation_probability,windspeed_10m,weathercode";
    private static final String OUT_OF_RANGE_REASON = "out of allowed range";
    /** Open-Meteo reports one decimal place, so this scale is lossless for its values. */
    private static final int VALUE_SCALE = 1;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenMeteoClient(RestClient.Builder builder,
                           @Value("${loc.weather.base-url}") String baseUrl,
                           @Value("${loc.weather.connect-timeout}") Duration connectTimeout,
                           @Value("${loc.weather.read-timeout}") Duration readTimeout) {
        this.restClient = builder
                .baseUrl(baseUrl)
                // A hung call would otherwise hold a queue worker for as long as it lasts.
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
                .defaultHeader("User-Agent", "loc-data/0.1")
                .build();
    }

    public WeatherLookup lookup(double latitude, double longitude, Instant hour) {
        String requestedHour = REQUEST_HOUR.format(hour);
        long startedAt = System.nanoTime();
        log.debug("Requesting forecast latitude={} longitude={} hour={}", latitude, longitude, requestedHour);

        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("hourly", HOURLY_FIELDS)
                            .queryParam("timezone", "UTC")
                            .queryParam("start_hour", requestedHour)
                            .queryParam("end_hour", requestedHour)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            return interpretErrorResponse(exception, requestedHour);
        } catch (RestClientException exception) {
            throw new WeatherServiceUnavailableException("Open-Meteo request failed after "
                    + (System.nanoTime() - startedAt) / 1_000_000 + "ms", exception);
        }

        log.debug("Received forecast response hour={} durationMs={}",
                requestedHour, (System.nanoTime() - startedAt) / 1_000_000);
        return interpret(body, requestedHour);
    }

    private WeatherLookup interpretErrorResponse(RestClientResponseException exception, String requestedHour) {
        int status = exception.getStatusCode().value();
        if (status == 429 || status >= 500) {
            throw new WeatherServiceUnavailableException("Open-Meteo returned status " + status, exception);
        }

        String reason = reason(exception.getResponseBodyAsString());
        if (reason != null && reason.contains(OUT_OF_RANGE_REASON)) {
            return new WeatherLookup.OutsideForecastRange(reason);
        }
        throw new WeatherRequestRejectedException("Open-Meteo rejected hour " + requestedHour
                + " with status " + status + (reason == null ? "" : ": " + reason));
    }

    private WeatherLookup interpret(String body, String requestedHour) {
        JsonNode hourly = readTree(body).path("hourly");
        int index = indexOfHour(hourly.path("time"), requestedHour);
        if (index < 0) {
            // Position in the array is never trusted: another hour is not an answer to what was asked.
            log.debug("Response carried no values for the requested hour hour={}", requestedHour);
            return new WeatherLookup.HourUnavailable();
        }

        BigDecimal temperature = decimal(hourly.path("temperature_2m"), index);
        Integer weatherCode = integer(hourly.path("weathercode"), index);
        if (temperature == null || weatherCode == null) {
            return new WeatherLookup.HourUnavailable();
        }

        return new WeatherLookup.Forecast(
                temperature,
                integer(hourly.path("precipitation_probability"), index),
                decimal(hourly.path("windspeed_10m"), index),
                weatherCode);
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            throw new WeatherServiceUnavailableException("Open-Meteo returned an empty response");
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new WeatherServiceUnavailableException("Could not parse the Open-Meteo response", exception);
        }
    }

    private String reason(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode reason = objectMapper.readTree(body).path("reason");
            return reason.isTextual() ? reason.asText() : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static int indexOfHour(JsonNode times, String requestedHour) {
        if (!times.isArray()) {
            return -1;
        }
        for (int index = 0; index < times.size(); index++) {
            if (requestedHour.equals(times.get(index).asText(null))) {
                return index;
            }
        }
        return -1;
    }

    private static BigDecimal decimal(JsonNode values, int index) {
        JsonNode value = values.path(index);
        return value.isNumber() ? value.decimalValue().setScale(VALUE_SCALE, RoundingMode.HALF_UP) : null;
    }

    private static Integer integer(JsonNode values, int index) {
        JsonNode value = values.path(index);
        return value.isNumber() ? value.intValue() : null;
    }
}
