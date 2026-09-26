package nl.loc.data.source.ticketmaster;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.collection.SourceCollector;
import nl.loc.data.collection.CollectionRun;
import nl.loc.data.collection.CollectionScope;
import nl.loc.data.collection.CollectedEvent;
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
    public CollectionScope scope(Instant startedAt) {
        Instant horizonStart = startedAt.atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant horizonEnd = horizonStart.atZone(ZoneOffset.UTC).plusMonths(HORIZON_MONTHS).toInstant();
        String country = ticketmasterClient.countryCode().toUpperCase(Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Ticketmaster collection requires one country code");
        }
        return new CollectionScope(false, country, horizonStart, horizonEnd);
    }

    @Override
    public List<CollectedEvent> collect(CollectionRun run) {
        String runId = run.getId().toString();
        Instant collectedAt = run.getStartedAt();
        Instant horizonStart = run.getScopeStartsAt();
        Instant horizonEnd = run.getScopeEndsAt();

        log.info("Starting Ticketmaster collection run {}", runId);

        long startedAt = System.nanoTime();
        Progress progress = new Progress();
        progress.windowStart = horizonStart;
        progress.windowEnd = horizonStart.plus(WINDOW);
        int storedEvents = 0;
        Set<String> seenEventIds = new HashSet<>();
        List<CollectedEvent> collectedEvents = new ArrayList<>();

        try {
            Instant windowStart = horizonStart;
            while (windowStart.isBefore(horizonEnd)) {
                Instant windowEnd = windowStart.plus(WINDOW);
                if (windowEnd.isAfter(horizonEnd)) {
                    windowEnd = horizonEnd;
                }
                progress.windowStart = windowStart;
                progress.windowEnd = windowEnd;
                progress.stage = "fetch-window";
                storedEvents += collectWindow(runId, collectedAt, windowStart, windowEnd,
                        seenEventIds, collectedEvents, progress);
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
        return List.copyOf(collectedEvents);
    }

    private int collectWindow(String runId,
                              Instant collectedAt,
                              Instant windowStart,
                              Instant windowEnd,
                              Set<String> seenEventIds,
                              List<CollectedEvent> collectedEvents,
                              Progress progress)
    {
        progress.windowStart = windowStart;
        progress.windowEnd = windowEnd;

        int pageSize = ticketmasterClient.pageSize();
        if (pageSize < 1 || pageSize > MAX_PAGE_OFFSET) {
            throw new IllegalArgumentException("Ticketmaster page size must be between 1 and " + MAX_PAGE_OFFSET);
        }
        List<JsonNode> pageEvents = new ArrayList<>();
        int page = 0;
        boolean hasMorePages = false;

        while (pageSize * page < MAX_PAGE_OFFSET) {
            progress.page = page;
            progress.stage = "fetch-page";
            String pageJson = ticketmasterClient.fetchEventsPage(windowStart, windowEnd, page);
            progress.stage = "parse-page";
            EventsPage result = parseEvents(pageJson, windowStart, windowEnd, page);
            JsonNode events = result.events();
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
            hasMorePages = page < result.totalPages();
            if (!hasMorePages) {
                break;
            }
        }

        if (pageEvents.size() >= MAX_PAGE_OFFSET || hasMorePages) {
            // Match the whole-second precision sent by TicketmasterClient.
            long startSecond = windowStart.getEpochSecond();
            long endSecond = windowEnd.getEpochSecond();
            if (endSecond - startSecond <= 1) {
                throw new IllegalStateException(
                        "Ticketmaster window still returns " + MAX_PAGE_OFFSET
                                + " events and cannot be split further at API second precision: "
                                + windowStart + " to " + windowEnd
                                + "; another partitioning strategy is required");
            }
            Instant midpoint = Instant.ofEpochSecond(startSecond + (endSecond - startSecond) / 2);
            log.info("Ticketmaster window hit paging cap; splitting runId={} windowStart={} midpoint={} windowEnd={}",
                    runId, windowStart, midpoint, windowEnd);
            return collectWindow(runId, collectedAt, windowStart, midpoint, seenEventIds, collectedEvents, progress)
                    + collectWindow(runId, collectedAt, midpoint, windowEnd, seenEventIds, collectedEvents, progress);
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
            collectedEvents.add(new CollectedEvent(eventId, key));
            newlyStored++;
        }
        return newlyStored;
    }

    private EventsPage parseEvents(String json, Instant windowStart, Instant windowEnd, int page) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Ticketmaster page was not a JSON object"
                        + " windowStart=" + windowStart + " windowEnd=" + windowEnd + " page=" + page);
            }
            JsonNode pagination = root.path("page");
            if (!pagination.path("totalElements").isIntegralNumber()
                    || !pagination.path("totalPages").isIntegralNumber()
                    || !pagination.path("number").isIntegralNumber()
                    || pagination.path("totalElements").asLong() < 0
                    || pagination.path("totalPages").asInt() < 0
                    || pagination.path("number").asInt() != page) {
                throw new IllegalStateException("Ticketmaster response has invalid pagination page=" + page);
            }
            long totalElements = pagination.path("totalElements").asLong();
            int totalPages = pagination.path("totalPages").asInt();
            if (totalElements > 0 && totalPages == 0) {
                throw new IllegalStateException("Ticketmaster non-empty result has no pages");
            }
            JsonNode events = root.path("_embedded").path("events");
            if (events.isMissingNode() || events.isNull()) {
                events = objectMapper.createArrayNode();
            }
            if (!events.isArray()) {
                throw new IllegalStateException("Ticketmaster _embedded.events was not a JSON array"
                        + " windowStart=" + windowStart + " windowEnd=" + windowEnd + " page=" + page);
            }
            if (events.isEmpty() && totalElements > 0 && page < totalPages) {
                throw new IllegalStateException("Ticketmaster returned an incomplete empty page=" + page);
            }
            if (!events.isEmpty() && (totalElements == 0 || page >= totalPages)) {
                throw new IllegalStateException("Ticketmaster events contradict pagination page=" + page);
            }
            return new EventsPage(events, totalPages);
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

    private record EventsPage(JsonNode events, int totalPages) {
    }
}
