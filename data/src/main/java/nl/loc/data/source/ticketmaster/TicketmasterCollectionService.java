package nl.loc.data.source.ticketmaster;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.collection.SourceCollector;
import nl.loc.data.storage.RawObjectStore;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketmasterCollectionService implements SourceCollector {

    private static final String SOURCE = "ticketmaster";
    private static final String JSON = "application/json";
    private static final Duration WINDOW = Duration.ofDays(30);
    private static final int HORIZON_MONTHS = 24;
    private static final int MAX_PAGE_OFFSET = 1000;

    private final TicketmasterClient ticketmasterClient;
    private final RawObjectStore rawObjectStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<String> collect() {
        String runId = UUID.randomUUID().toString();
        Instant collectedAt = Instant.now();
        Instant horizonStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant horizonEnd = horizonStart.atZone(ZoneOffset.UTC).plusMonths(HORIZON_MONTHS).toInstant();

        log.info("Starting Ticketmaster collection run {}", runId);

        long startedAt = System.nanoTime();
        Progress progress = new Progress();
        progress.windowStart = horizonStart;
        progress.windowEnd = horizonStart.plus(WINDOW);
        int storedEvents = 0;
        Set<String> seenEventIds = new HashSet<>();
        List<String> eventKeys = new ArrayList<>();

        try {
            Instant windowStart = horizonStart;
            while (windowStart.isBefore(horizonEnd)) {
                Instant windowEnd = windowStart.plus(WINDOW);
                progress.windowStart = windowStart;
                progress.windowEnd = windowEnd;
                progress.stage = "fetch-window";
                storedEvents += collectWindow(runId, collectedAt, windowStart, windowEnd,
                        seenEventIds, eventKeys, progress);
                windowStart = windowEnd;
            }
        } catch (RuntimeException exception) {
            log.error("Ticketmaster collection failed runId={} windowStart={} windowEnd={} page={} eventId={} stage={} storedEvents={} durationMs={}",
                    runId, progress.windowStart, progress.windowEnd, progress.page, progress.eventId,
                    progress.stage, storedEvents, (System.nanoTime() - startedAt) / 1_000_000, exception);
            throw exception;
        }

        log.info("Finished Ticketmaster collection runId={} storedEvents={} durationMs={}",
                runId, storedEvents, (System.nanoTime() - startedAt) / 1_000_000);
        return List.copyOf(eventKeys);
    }

    private int collectWindow(String runId,
                              Instant collectedAt,
                              Instant windowStart,
                              Instant windowEnd,
                              Set<String> seenEventIds,
                              List<String> eventKeys,
                              Progress progress)
    {
        progress.windowStart = windowStart;
        progress.windowEnd = windowEnd;

        int pageSize = ticketmasterClient.pageSize();
        List<JsonNode> pageEvents = new ArrayList<>();
        int page = 0;

        while (pageSize * page < MAX_PAGE_OFFSET) {
            progress.page = page;
            progress.stage = "fetch-page";
            String pageJson = ticketmasterClient.fetchEventsPage(windowStart, windowEnd, page);
            progress.stage = "parse-page";
            JsonNode events = parseEvents(pageJson, windowStart, windowEnd, page);
            if (events.isEmpty()) {
                log.debug("Reached empty Ticketmaster page runId={} windowStart={} windowEnd={} page={}",
                        runId, windowStart, windowEnd, page);
                break;
            }
            for (JsonNode event : events) {
                pageEvents.add(event);
            }
            log.debug("Collected Ticketmaster page runId={} windowStart={} windowEnd={} page={} windowEventCount={}",
                    runId, windowStart, windowEnd, page, pageEvents.size());
            page++;
        }

        if (pageEvents.size() >= MAX_PAGE_OFFSET) {
            Duration half = Duration.between(windowStart, windowEnd).dividedBy(2);
            Instant midpoint = windowStart.plus(half);
            if (half.isZero() || !midpoint.isAfter(windowStart) || !midpoint.isBefore(windowEnd)) {
                throw new IllegalStateException(
                        "Ticketmaster window still returns " + MAX_PAGE_OFFSET
                                + " events and cannot be split further: " + windowStart + " to " + windowEnd);
            }
            log.info("Ticketmaster window hit paging cap; splitting runId={} windowStart={} midpoint={} windowEnd={}",
                    runId, windowStart, midpoint, windowEnd);
            return collectWindow(runId, collectedAt, windowStart, midpoint, seenEventIds, eventKeys, progress)
                    + collectWindow(runId, collectedAt, midpoint, windowEnd, seenEventIds, eventKeys, progress);
        }

        progress.stage = "store-events";
        int newlyStored = 0;
        for (JsonNode event : pageEvents) {
            JsonNode idNode = event.get("id");
            if (idNode == null || idNode.asText().isBlank()) {
                throw new IllegalStateException("Ticketmaster event is missing id");
            }
            String eventId = idNode.asText();
            progress.eventId = eventId;
            if (!seenEventIds.add(eventId)) {
                continue;
            }
            String key = rawKey(runId, "events/" + eventId + ".json");
            store(key, event, collectedAt);
            eventKeys.add(key);
            newlyStored++;
        }
        return newlyStored;
    }

    private JsonNode parseEvents(String json, Instant windowStart, Instant windowEnd, int page) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Ticketmaster page was not a JSON object"
                        + " windowStart=" + windowStart + " windowEnd=" + windowEnd + " page=" + page);
            }
            JsonNode events = root.path("_embedded").path("events");
            if (events.isMissingNode() || events.isNull()) {
                return objectMapper.createArrayNode();
            }
            if (!events.isArray()) {
                throw new IllegalStateException("Ticketmaster _embedded.events was not a JSON array"
                        + " windowStart=" + windowStart + " windowEnd=" + windowEnd + " page=" + page);
            }
            return events;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not parse Ticketmaster page"
                    + " windowStart=" + windowStart + " windowEnd=" + windowEnd + " page=" + page, exception);
        }
    }

    private void store(String key, JsonNode event, Instant collectedAt) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            rawObjectStore.put(key, payload, JSON, Map.of("collectedAt", collectedAt.toString()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Ticketmaster event", exception);
        }
    }

    private static String rawKey(String runId, String file) {
        return "raw/" + SOURCE + "/" + runId + "/" + file;
    }

    private static final class Progress {
        private Instant windowStart;
        private Instant windowEnd;
        private int page;
        private String eventId;
        private String stage = "init";
    }
}
