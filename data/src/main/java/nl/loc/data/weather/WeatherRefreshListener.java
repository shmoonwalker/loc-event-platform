package nl.loc.data.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Consumes refresh work. Concurrency is capped in configuration to spread the outbound calls. */
@Slf4j
@Component
@ConditionalOnProperty(name = "loc.weather.enabled", havingValue = "true")
@RequiredArgsConstructor
public class WeatherRefreshListener {

    private final WeatherEnrichmentService weatherEnrichmentService;

    /**
     * The body is only a time slot id, so a duplicate message is harmless: everything else is
     * re-read from the catalog and the write is rejected if the slot no longer matches.
     */
    @RabbitListener(queues = WeatherMessaging.REFRESH_QUEUE)
    public void onRefreshRequested(String body) {
        long timeSlotId;
        try {
            timeSlotId = Long.parseLong(body.strip());
        } catch (NumberFormatException | NullPointerException exception) {
            log.error("Discarding an unreadable weather refresh message body={}", body);
            throw new AmqpRejectAndDontRequeueException("Unreadable weather refresh message: " + body, exception);
        }

        log.debug("Handling weather refresh request timeSlotId={}", timeSlotId);
        weatherEnrichmentService.refresh(timeSlotId);
    }
}
