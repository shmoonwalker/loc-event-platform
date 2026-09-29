package nl.loc.data.tagging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiTagClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;
    private final String apiKey;

    public GeminiTagClient(RestClient.Builder builder,
                           @Value("${loc.tagging.base-url}") String baseUrl,
                           @Value("${loc.tagging.model}") String model,
                           @Value("${loc.tagging.api-key}") String apiKey,
                           @Value("${loc.tagging.connect-timeout}") Duration connectTimeout,
                           @Value("${loc.tagging.read-timeout}") Duration readTimeout) {
        this.model = model;
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
                .build();
    }

    public List<ContentTag> tag(TagTarget target) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TaggingRejectedException("GeminiAPIKey is blank");
        }
        String prompt = TagPrompt.text(
                String.join(", ", target.categoryNames()),
                target.title(),
                target.descriptionForPrompt());
        long startedAt = System.nanoTime();
        log.debug("Requesting tags eventId={} model={}", target.eventId(), model);

        String body;
        try {
            body = restClient.post()
                    // The colon must stay literal. A "{model}:action" template is read as a regex and drops the model.
                    .uri("/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(prompt))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw interpretStatus(exception);
        } catch (RestClientException exception) {
            throw new TaggingUnavailableException("Gemini request failed after "
                    + (System.nanoTime() - startedAt) / 1_000_000 + "ms", exception);
        }

        log.debug("Received tag response eventId={} durationMs={}",
                target.eventId(), (System.nanoTime() - startedAt) / 1_000_000);
        return parse(body);
    }

    private String requestBody(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);
        ObjectNode generation = root.putObject("generationConfig");
        generation.put("temperature", 0.2);
        generation.put("maxOutputTokens", 256);
        generation.put("responseMimeType", "application/json");
        generation.putObject("thinkingConfig").put("thinkingLevel", "minimal");
        return root.toString();
    }

    private RuntimeException interpretStatus(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String reason = exception.getResponseBodyAsString();
        if (reason != null && reason.length() > 300) {
            reason = reason.substring(0, 300);
        }
        String detail = reason == null || reason.isBlank() ? "" : ": " + reason;
        if (status == 429 || status >= 500) {
            return new TaggingUnavailableException("Gemini returned status " + status + detail, exception);
        }
        return new TaggingRejectedException("Gemini rejected the tag request with status " + status + detail);
    }

    private List<ContentTag> parse(String body) {
        if (body == null || body.isBlank()) {
            throw new TaggingUnavailableException("Gemini returned an empty response");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new TaggingUnavailableException("Could not parse the Gemini response", exception);
        }

        String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (text == null || text.isBlank()) {
            throw new TaggingUnavailableException("Gemini returned no tag text");
        }
        JsonNode tags;
        try {
            tags = objectMapper.readTree(stripFences(text)).path("tags");
        } catch (JsonProcessingException exception) {
            throw new TaggingUnavailableException("Gemini tag JSON was unreadable", exception);
        }
        if (!tags.isArray()) {
            throw new TaggingUnavailableException("Gemini tag JSON has no tags array");
        }

        Set<ContentTag> accepted = new LinkedHashSet<>();
        for (JsonNode tag : tags) {
            if (!tag.isTextual() || accepted.size() >= TagPrompt.MAX_GEMINI_TAGS) {
                continue;
            }
            ContentTag.fromSlug(tag.asText())
                    .filter(ContentTag::geminiAllowed)
                    .ifPresent(accepted::add);
        }
        return new ArrayList<>(accepted);
    }

    private static String stripFences(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstBreak < 0 || lastFence <= firstBreak) {
            return trimmed;
        }
        return trimmed.substring(firstBreak + 1, lastFence).strip();
    }
}
