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

        long startedAt = System.nanoTime();
        String stage = "fetch-list";
        String currentEventId = null;
        int page = 0;
        int storedPages = 0;
        int storedEvents = 0;

        try {
            while (true) {
                currentEventId = null;
                stage = "fetch-list";
                String listJson = rvoClient.fetchListPage(page);
                stage = "parse-list";
                JsonNode events = parseArray(listJson, "list page " + page);
                if (events.isEmpty()) {
                    log.debug("Reached empty RVO page runId={} page={}", runId, page);
                    break;
                }

                stage = "store-list";
                store(rawKey(runId, "page-%03d.json".formatted(page)), listJson);
                storedPages++;

                stage = "read-event-ids";
                for (String id : eventIds(events)) {
                    currentEventId = id;
                    stage = "fetch-detail";
                    String detailJson = rvoClient.fetchEvent(id);
                    stage = "store-detail";
                    store(rawKey(runId, "events/" + id + ".json"), detailJson);
                    storedEvents++;
                }

                log.debug("Collected RVO page runId={} page={} storedEvents={}", runId, page, storedEvents);
                page++;
            }
        } catch (RuntimeException exception) {
            log.error("RVO collection failed runId={} page={} eventId={} stage={} storedPages={} storedEvents={} durationMs={}",
                    runId, page, currentEventId, stage, storedPages, storedEvents,
                    (System.nanoTime() - startedAt) / 1_000_000, exception);
            throw exception;
        }

        log.info("Finished RVO collection runId={} storedPages={} storedEvents={} durationMs={}",
                runId, storedPages, storedEvents, (System.nanoTime() - startedAt) / 1_000_000);
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
