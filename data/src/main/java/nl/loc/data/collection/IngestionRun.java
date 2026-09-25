package nl.loc.data.collection;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.processing.RawEventProcessingService;
import nl.loc.data.processing.SourceEventMapper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionRun {

    private final List<SourceCollector> collectors;
    private final List<SourceEventMapper> mappers;
    private final RawEventProcessingService rawEventProcessingService;

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
        List<String> detailKeys = collector.collect();
        log.info("Collected source={} detailCount={}", collector.source(), detailKeys.size());
        if (!hasMapper(collector.source())) {
            log.info("Processing skipped source={} reason=no mapper registered storedCount={}",
                    collector.source(), detailKeys.size());
            log.info("Ingestion finished source={} published=0 failed=0", collector.source());
            return;
        }
        publishStored(collector.source(), detailKeys);
    }

    public void reprocess(String source, List<String> detailKeys) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (detailKeys == null) {
            throw new IllegalArgumentException("detailKeys is required");
        }
        log.info("Reprocessing source={} detailCount={}", source, detailKeys.size());
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
