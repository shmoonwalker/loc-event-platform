package nl.loc.data.tagging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "loc.tagging.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TagRefreshListener {

    private final TagEnrichmentService tagEnrichmentService;

    @RabbitListener(queues = TagMessaging.QUEUE, concurrency = "1")
    public void onTagRequested(String body) {
        long eventId;
        try {
            eventId = Long.parseLong(body.strip());
        } catch (NumberFormatException | NullPointerException exception) {
            log.error("Discarding an unreadable tag message body={}", body);
            throw new AmqpRejectAndDontRequeueException("Unreadable tag message: " + body, exception);
        }
        log.debug("Handling tag request eventId={}", eventId);
        tagEnrichmentService.tag(eventId);
    }
}
