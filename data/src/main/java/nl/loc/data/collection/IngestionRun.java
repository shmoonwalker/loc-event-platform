package nl.loc.data.collection;

import java.util.List;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.processing.RawEventProcessingService;
import nl.loc.data.processing.SourceEventMapper;
import nl.loc.data.processing.RawProcessingRepository;
import nl.loc.data.storage.RawObjectStore;
import nl.loc.data.storage.StoredRawObject;
import nl.loc.data.catalog.CatalogPresenceService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionRun {

    private final List<SourceCollector> collectors;
    private final List<SourceEventMapper> mappers;
    private final RawEventProcessingService rawEventProcessingService;
    private final CatalogPresenceService catalogPresenceService;
    private final CollectionRunRepository collectionRunRepository;
    private final RawObjectStore rawObjectStore;
    private final RawProcessingRepository processingRepository;

    public void processPendingAll() {
        for (SourceEventMapper mapper : mappers) {
            try {
                processPending(mapper.source());
            } catch (RuntimeException exception) {
                log.error("Stored event processing failed source={}", mapper.source(), exception);
            }
        }
    }

    public void processPending(String source) {
        if (!hasMapper(source)) {
            throw new IllegalArgumentException("No mapper for source " + source);
        }
        // Retry reconciliation too if a prior run stopped after saving its completion record.
        catalogPresenceService.reconcile(source);
        Pattern eventKey = Pattern.compile("raw/" + Pattern.quote(source) + "/[^/]+/events/[^/]+\\.json");
        List<String> pending = rawObjectStore.list("raw/" + source + "/").stream()
                .filter(object -> eventKey.matcher(object.key()).matches())
                .sorted(Comparator.comparing(StoredRawObject::lastModified,
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(StoredRawObject::key))
                .map(StoredRawObject::key)
                .filter(key -> !processingRepository.isProcessed(source, key))
                .toList();
        log.info("Discovered pending raw events source={} count={}", source, pending.size());
        publishStored(source, pending);
    }

    public void runAll() {
        log.info("Running ingestion sourceCount={}", collectors.size());
        for (SourceCollector collector : collectors) {
            try {
                run(collector.source());
            } catch (RuntimeException exception) {
                log.error("Ingestion failed source={}", collector.source(), exception);
            }
        }
    }

    public void run(String source) {
        SourceCollector collector = collectorFor(source);
        log.info("Collecting source={}", collector.source());
        Instant startedAt = Instant.now();
        CollectionRun run = collectionRunRepository.save(
                new CollectionRun(source, startedAt, collector.scope(startedAt)));
        List<CollectedEvent> collectedEvents;
        try {
            collectedEvents = collector.collect(run);
        } catch (RuntimeException exception) {
            run.fail(Instant.now());
            collectionRunRepository.save(run);
            throw exception;
        }
        run.complete(Instant.now(), collectedEvents.stream().map(CollectedEvent::externalId).distinct().toList());
        collectionRunRepository.save(run);
        log.info("Collected source={} detailCount={}", collector.source(), collectedEvents.size());
        if (!hasMapper(collector.source())) {
            log.info("Processing skipped source={} reason=no mapper registered storedCount={}",
                    collector.source(), collectedEvents.size());
            log.info("Ingestion finished source={} published=0 failed=0", collector.source());
            return;
        }
        processPending(collector.source());
    }

    public void reprocess(String source, List<String> detailKeys) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (detailKeys == null) {
            throw new IllegalArgumentException("detailKeys is required");
        }
        log.info("Reprocessing source={} detailCount={}", source, detailKeys.size());
        catalogPresenceService.reconcile(source);
        publishStored(source, detailKeys);
    }

    private void publishStored(String source, List<String> detailKeys) {
        int published = 0;
        int failed = 0;
        for (String detailKey : detailKeys) {
            try {
                rawEventProcessingService.process(source, detailKey);
                published++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("Skipping failed detail file source={} rawObjectKey={}", source, detailKey, exception);
            }
        }
        log.info("Ingestion finished source={} published={} failed={}", source, published, failed);
    }

    private boolean hasMapper(String source) {
        for (SourceEventMapper mapper : mappers) {
            if (mapper.source().equals(source)) {
                return true;
            }
        }
        return false;
    }

    private SourceCollector collectorFor(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        for (SourceCollector collector : collectors) {
            if (collector.source().equals(source)) {
                return collector;
            }
        }
        throw new IllegalArgumentException("No collector for source " + source);
    }
}
