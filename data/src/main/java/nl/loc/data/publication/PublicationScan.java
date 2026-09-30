package nl.loc.data.publication;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A bounded page scan also detects optional enrichment and rule changes without re-importing raw files. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "loc.publication.enabled", havingValue = "true", matchIfMissing = true)
public class PublicationScan {
    private final PublicationService publication;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void ready() { ready = true; scan(); }

    @Scheduled(fixedDelayString = "${loc.publication.scan-interval:PT1M}", initialDelayString = "PT1M")
    public void scan() {
        if (!ready || !scanning.compareAndSet(false, true)) return;
        try {
            long after = 0;
            while (true) {
                List<Long> ids = publication.page(after, 100);
                if (ids.isEmpty()) break;
                for (long id : ids) {
                    try { publication.evaluate(id); }
                    catch (RuntimeException exception) { log.error("Publication evaluation failed eventId={}", id, exception); }
                }
                after = ids.getLast();
            }
        } finally { scanning.set(false); }
    }
}
