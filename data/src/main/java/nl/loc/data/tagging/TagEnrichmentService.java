package nl.loc.data.tagging;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import nl.loc.data.event.Category;
import nl.loc.data.publication.PublicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Category tags are written here without Gemini. Gemini runs only for an event publication would
 * already accept, and only when the description is long enough. A failed call is stored so the
 * queue can retry it.
 */
@Slf4j
@Service
public class TagEnrichmentService {

    private static final Duration FIRST_BACKOFF = Duration.ofHours(1);
    private static final Duration MAX_BACKOFF = Duration.ofHours(24);
    private static final int MAX_BACKOFF_DOUBLINGS = 5;

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

        List<ContentTag> categoryTags = categoryTags(target);
        tagRepository.replaceCategoryTags(eventId, categoryTags);

        String fingerprint = TagRepository.fingerprint(target.title(), target.description());
        if (tagRepository.alreadyTagged(eventId, fingerprint, TagPrompt.VERSION)) {
            log.debug("Skipping Gemini eventId={} reason=title and description are unchanged", eventId);
            return;
        }
        if (!target.geminiEligible()) {
            tagRepository.replaceGeminiTags(eventId, List.of());
            tagRepository.saveOutcome(eventId, fingerprint, "NOT_APPLICABLE", Instant.now(),
                    null, 0, null, null);
            log.debug("Stored category tags only eventId={} tags={} reason=description is too thin for Gemini",
                    eventId, slugs(categoryTags));
            return;
        }

        PublicationService.Readiness readiness = publicationService.readiness(eventId);
        if (!readiness.eligible()) {
            tagRepository.holdUntil(eventId, fingerprint, readiness.retryAt(), String.join(",", readiness.reasons()));
            log.info("Skipping Gemini eventId={} retryAt={} reasons={}", eventId, readiness.retryAt(), readiness.reasons());
            return;
        }

        Instant now = Instant.now();
        int usedToday = tagRepository.geminiAttemptsSince(startOfUtcDay(now));
        if (usedToday >= dailyLimit) {
            Instant tomorrow = startOfUtcDay(now).plus(Duration.ofDays(1));
            tagRepository.defer(eventId, tomorrow);
            log.info("Deferring Gemini eventId={} usedToday={} dailyLimit={} nextCheckAt={}",
                    eventId, usedToday, dailyLimit, tomorrow);
            return;
        }

        try {
            List<ContentTag> geminiTags = geminiTagClient.tag(target);
            tagRepository.replaceGeminiTags(eventId, geminiTags);
            tagRepository.saveOutcome(eventId, fingerprint, "SUCCEEDED", now, null, 0, now, null);
            log.debug("Stored tags eventId={} categoryTags={} geminiTags={}",
                    eventId, slugs(categoryTags), slugs(geminiTags));
        } catch (TaggingRejectedException exception) {
            Instant nextCheckAt = recordFailure(target, now, exception.getMessage());
            log.warn("Gemini rejected tagging eventId={} attempts={} nextCheckAt={}",
                    eventId, target.attempts() + 1, nextCheckAt, exception);
        } catch (TaggingUnavailableException exception) {
            Instant nextCheckAt = recordFailure(target, now, exception.getMessage());
            log.warn("Gemini tagging failed eventId={} attempts={} nextCheckAt={} reason={}",
                    eventId, target.attempts() + 1, nextCheckAt, exception.getMessage());
            throw exception;
        }
    }

    private Instant recordFailure(TagTarget target, Instant now, String error) {
        int attempts = target.attempts() + 1;
        int doublings = Math.min(Math.max(attempts, 1) - 1, MAX_BACKOFF_DOUBLINGS);
        Duration backoff = FIRST_BACKOFF.multipliedBy(1L << doublings);
        if (backoff.compareTo(MAX_BACKOFF) > 0) {
            backoff = MAX_BACKOFF;
        }
        Instant nextCheckAt = now.plus(backoff);
        tagRepository.saveOutcome(target.eventId(), TagRepository.fingerprint(target.title(), target.description()),
                "FAILED", null, nextCheckAt, attempts, now, error);
        return nextCheckAt;
    }

    private static List<ContentTag> categoryTags(TagTarget target) {
        List<ContentTag> tags = new ArrayList<>();
        for (String name : target.categoryNames()) {
            for (Category category : Category.values()) {
                if (category.catalogName().equals(name)) {
                    ContentTag.fromCategory(category).ifPresent(tags::add);
                }
            }
        }
        return tags.stream().distinct().toList();
    }

    private static List<String> slugs(List<ContentTag> tags) {
        return tags.stream().map(ContentTag::slug).toList();
    }

    private static Instant startOfUtcDay(Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return today.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
