package nl.loc.data.tagging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "loc.tagging.enabled", havingValue = "true")
public class TagRefreshScan {

    private final TagRepository tagRepository;
    private final RabbitTemplate rabbitTemplate;
    private final int scanLimit;
    private final Duration claimTtl;
    private final boolean apiKeyConfigured;

    public TagRefreshScan(TagRepository tagRepository,
                          RabbitTemplate rabbitTemplate,
                          @Value("${loc.tagging.scan-limit}") int scanLimit,
                          @Value("${loc.tagging.claim-ttl}") Duration claimTtl,
                          @Value("${loc.tagging.api-key}") String apiKey) {
        this.tagRepository = tagRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.scanLimit = scanLimit;
        this.claimTtl = claimTtl;
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
        if (!apiKeyConfigured) {
            log.warn("GeminiAPIKey is blank; events with a description stay held with AWAITING_TAGS");
        }
    }

    @Scheduled(fixedDelayString = "${loc.tagging.scan-interval}",
            initialDelayString = "${loc.tagging.scan-initial-delay}")
    public void queueDueTagging() {
        if (!apiKeyConfigured) {
            return;
        }
        long startedAt = System.nanoTime();
        Instant now = Instant.now();
        String stage = "find-due-events";
        try {
            List<Long> due = tagRepository.findDueEventIds(now, scanLimit);
            if (due.isEmpty()) {
                log.debug("No tag jobs due");
                return;
            }

            stage = "queue-tags";
            Instant claimUntil = now.plus(claimTtl);
            int queued = 0;
            for (Long eventId : due) {
                try {
                    tagRepository.claim(eventId, claimUntil);
                    rabbitTemplate.convertAndSend(TagMessaging.EXCHANGE, TagMessaging.ROUTING_KEY,
                            Long.toString(eventId));
                    queued++;
                } catch (RuntimeException exception) {
                    log.error("Could not queue tagging eventId={}", eventId, exception);
                }
            }
            log.info("Queued tag jobs queued={} due={} limit={} claimUntil={} durationMs={}",
                    queued, due.size(), scanLimit, claimUntil, (System.nanoTime() - startedAt) / 1_000_000);
            if (due.size() >= scanLimit) {
                log.info("Tag scan filled its limit; remaining events wait for the next scan limit={}", scanLimit);
            }
        } catch (RuntimeException exception) {
            log.error("Tag scan failed stage={} durationMs={}",
                    stage, (System.nanoTime() - startedAt) / 1_000_000, exception);
        }
    }
}
