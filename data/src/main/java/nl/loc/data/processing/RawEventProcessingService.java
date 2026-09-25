package nl.loc.data.processing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.catalog.CatalogPublicationService;
import nl.loc.data.event.NormalizedEvent;
import nl.loc.data.storage.RawObject;
import nl.loc.data.storage.RawObjectStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawEventProcessingService {

    private final RawObjectStore rawObjectStore;
    private final List<SourceEventMapper> mappers;
    private final CatalogPublicationService catalogPublicationService;

    public void process(String source, String rawObjectKey) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (rawObjectKey == null || rawObjectKey.isBlank()) {
            throw new IllegalArgumentException("rawObjectKey is required");
        }

        log.info("Processing raw object source={} rawObjectKey={}", source, rawObjectKey);

        long startedAt = System.nanoTime();
        String stage = "find-mapper";
        try {
            SourceEventMapper mapper = mapperFor(source);

            stage = "read-raw-object";
            RawObject rawObject = rawObjectStore.get(rawObjectKey);
            String rawJson = new String(rawObject.payload(), StandardCharsets.UTF_8);
            stage = "read-collection-metadata";
            Instant collectedAt = collectedAt(rawObject, rawObjectKey);

            stage = "map-events";
            List<NormalizedEvent> events = mapper.map(rawJson, rawObjectKey);

            stage = "publish-events";
            for (NormalizedEvent event : events) {
                catalogPublicationService.publish(event, collectedAt);
            }

            log.info("Published events source={} count={} rawObjectKey={} durationMs={}",
                    source, events.size(), rawObjectKey, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException exception) {
            log.error("Processing failed source={} rawObjectKey={} stage={} durationMs={}",
                    source, rawObjectKey, stage, (System.nanoTime() - startedAt) / 1_000_000, exception);
            throw exception;
        }
    }

    private static Instant collectedAt(RawObject rawObject, String rawObjectKey) {
        // S3-compatible stores may return user metadata keys in lowercase.
        String value = rawObject.metadata().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("collectedAt"))
                .map(entry -> entry.getValue())
                .findFirst()
                .orElse(null);
        if (value == null) {
            return null;
        }
        try {
            // Match PostgreSQL precision so replaying this object compares equal after persistence.
            return Instant.parse(value).truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid collectedAt metadata for raw object " + rawObjectKey, exception);
        }
    }

    private SourceEventMapper mapperFor(String source) {
        for (SourceEventMapper mapper : mappers) {
            if (mapper.source().equals(source)) {
                return mapper;
            }
        }
        throw new IllegalArgumentException("No mapper for source " + source);
    }
}
