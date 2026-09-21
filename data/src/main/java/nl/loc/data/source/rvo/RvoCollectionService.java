package nl.loc.data.source.rvo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.storage.RawObjectStore;

@Slf4j
@Service
@RequiredArgsConstructor
public class RvoCollectionService {

    private static final String SOURCE = "rvo";
    private static final String JSON = "application/json";

    private final RvoClient rvoClient;
    private final RawObjectStore rawObjectStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void collect() {
        String runId = UUID.randomUUID().toString();
        log.info("Starting RVO collection run {}", runId);

        int page = 0;
        int storedPages = 0;
        int storedEvents = 0;

        while (true) {
            String listJson = rvoClient.fetchListPage(page);
            JsonNode events = parseArray(listJson, "list page " + page);
            if (events.isEmpty()) {
                break;
            }

            store(rawKey(runId, "page-%03d.json".formatted(page)), listJson);
            storedPages++;

            for (String id : eventIds(events)) {
                String detailJson = rvoClient.fetchEvent(id);
                store(rawKey(runId, "events/" + id + ".json"), detailJson);
                storedEvents++;
            }

            page++;
        }

        log.info("Finished RVO collection run {} ({} pages, {} events)", runId, storedPages, storedEvents);
    }

    private List<String> eventIds(JsonNode events) {
        List<String> ids = new ArrayList<>();
        for (JsonNode event : events) {
            JsonNode id = event.get("id");
            if (id == null || id.asText().isBlank()) {
                throw new IllegalStateException("RVO list event is missing id");
            }
            ids.add(id.asText());
        }
        return ids;
    }

    private JsonNode parseArray(String json, String label) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                throw new IllegalStateException("RVO " + label + " was not a JSON array");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not parse RVO " + label, exception);
        }
    }

    private void store(String key, String json) {
        rawObjectStore.put(key, json.getBytes(StandardCharsets.UTF_8), JSON);
    }

    private static String rawKey(String runId, String file) {
        return "raw/" + SOURCE + "/" + runId + "/" + file;
    }
}
