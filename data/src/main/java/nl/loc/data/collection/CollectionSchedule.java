package nl.loc.data.collection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Collects each source again once its last completed collection is older than the interval.
 * Publication withdraws events whose details are older than the freshness limit, so without
 * this the product empties when nobody runs a collection by hand.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "loc.collection.enabled", havingValue = "true")
public class CollectionSchedule {

    private final IngestionRun ingestionRun;
    private final CollectionRunRepository collectionRunRepository;
    private final List<SourceCollector> collectors;
    private final Duration interval;

    public CollectionSchedule(IngestionRun ingestionRun,
                              CollectionRunRepository collectionRunRepository,
                              List<SourceCollector> collectors,
                              @Value("${loc.collection.interval}") Duration interval) {
        this.ingestionRun = ingestionRun;
        this.collectionRunRepository = collectionRunRepository;
        this.collectors = collectors;
        this.interval = interval;
    }

    @Scheduled(fixedDelayString = "${loc.collection.check-interval}",
            initialDelayString = "${loc.collection.initial-delay}")
    public void collectDueSources() {
        Instant now = Instant.now();
        for (SourceCollector collector : collectors) {
            String source = collector.source();
            Instant lastCompleted = collectionRunRepository
                    .findBySourceAndStatusOrderByStartedAtDescIdDesc(source, "COMPLETED").stream()
                    .findFirst().map(CollectionRun::getStartedAt).orElse(null);
            if (lastCompleted != null && lastCompleted.plus(interval).isAfter(now)) {
                continue;
            }
            log.info("Scheduled collection source={} lastCompleted={}", source, lastCompleted);
            try {
                ingestionRun.run(source);
            } catch (RuntimeException exception) {
                log.error("Scheduled collection failed source={}", source, exception);
            }
        }
    }
}
