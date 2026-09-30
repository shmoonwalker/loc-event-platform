package nl.loc.data.tagging;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import nl.loc.data.publication.PublicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends an event to Gemini once, when missing tags are the only thing keeping it out of the product.
 * Local tags are written at import. A failure is stored with a backoff instead of being retried by
 * the queue, because every retry would spend another free-tier request.
 */
@Slf4j
@Service
public class TagEnrichmentService {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration FIRST_BACKOFF = Duration.ofHours(1);
    private static final Duration MAX_BACKOFF = Duration.ofHours(24);

    private final TagRepository tagRepository;
    private final GeminiTagClient geminiTagClient;
    private final PublicationService publicationService;
    private final int dailyLimit;

    public TagEnrichmentService(TagRepository tagRepository,
                                GeminiTagClient geminiTagClient,
                                PublicationService publicationService,
                                @Value("${loc.tagging.daily-limit}") int dailyLimit) {
        this.tagRepository = tagRepository;
        this.geminiTagClient = geminiTagClient;
        this.publicationService = publicationService;
        this.dailyLimit = dailyLimit;
    }

    public void tag(long eventId) {
        TagTarget target = tagRepository.findActive(eventId);
        if (target == null) {
            log.debug("Skipping tags eventId={} reason=event is no longer active", eventId);
            return;
        }
        if (tagRepository.alreadyTagged(eventId)) {
            log.debug("Skipping Gemini eventId={} reason=already tagged", eventId);
            return;
        }
        if (!target.geminiEligible()) {
            log.debug("Skipping Gemini eventId={} reason=description is too short", eventId);
            return;
        }
        if (!publicationService.readyForTagging(eventId)) {
            log.debug("Skipping Gemini eventId={} reason=event fails other publication rules", eventId);
            return;
        }

        Instant now = Instant.now();
        if (!tagRepository.reserveGeminiAttempt(now, dailyLimit, eventId)) {
            Instant tomorrow = startOfUtcDay(now).plus(Duration.ofDays(1));
            tagRepository.defer(eventId, tomorrow);
            log.info("Deferring Gemini eventId={} dailyLimit={} nextCheckAt={}", eventId, dailyLimit, tomorrow);
            return;
        }

        String fingerprint = TagRepository.fingerprint(target.title(), target.description());
        try {
            List<ContentTag> geminiTags = geminiTagClient.tag(target);
            tagRepository.replaceGeminiTags(eventId, geminiTags);
            tagRepository.saveOutcome(eventId, fingerprint, "SUCCEEDED", now, null, 0, now, null);
            log.debug("Stored Gemini tags eventId={} tags={}", eventId, geminiTags.stream().map(ContentTag::slug).toList());
        } catch (TaggingRejectedException | TaggingUnavailableException exception) {
            recordFailure(target, fingerprint, now, exception.getMessage());
        }
    }

    private void recordFailure(TagTarget target, String fingerprint, Instant now, String error) {
        int attempts = target.attempts() + 1;
        if (attempts >= MAX_ATTEMPTS) {
            tagRepository.saveOutcome(target.eventId(), fingerprint, "GAVE_UP", null, null, attempts, now, error);
            log.warn("Gemini gave up eventId={} attempts={} reason={}; publishing with local tags",
                    target.eventId(), attempts, error);
            return;
        }
        Duration backoff = FIRST_BACKOFF.multipliedBy(1L << (attempts - 1));
        if (backoff.compareTo(MAX_BACKOFF) > 0) {
            backoff = MAX_BACKOFF;
        }
        Instant nextCheckAt = now.plus(backoff);
        tagRepository.saveOutcome(target.eventId(), fingerprint, "FAILED", null, nextCheckAt, attempts, now, error);
        log.warn("Gemini tagging failed eventId={} attempts={} nextCheckAt={} reason={}",
                target.eventId(), attempts, nextCheckAt, error);
    }

    private static Instant startOfUtcDay(Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return today.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
