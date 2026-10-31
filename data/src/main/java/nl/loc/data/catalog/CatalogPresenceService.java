package nl.loc.data.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import nl.loc.data.collection.CollectionRun;
import nl.loc.data.collection.CollectionRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Only completed, scoped collections can establish that an event disappeared. */
@Service
@RequiredArgsConstructor
public class CatalogPresenceService {
    private final CatalogEventRepository eventRepository;
    private final CatalogEventLocationRepository locationRepository;
    private final CatalogEventTimeSlotRepository slotRepository;
    private final CollectionRunRepository runRepository;
    private final CatalogSourceLock sourceLock;

    @Transactional
    public void reconcile(String source) {
        sourceLock.acquire(source);
        List<Observation> observations = observations(source);
        List<CatalogEvent> events = eventRepository.findBySource(source);
        // Load scope facts once: a query per event made Hibernate auto-flush every loaded event each time.
        Map<Long, String> countries = new HashMap<>();
        locationRepository.findByEventSource(source)
                .forEach(location -> countries.put(location.getId(), location.getCountryCode()));
        Map<Long, List<Instant>> starts = new HashMap<>();
        slotRepository.findByEventSourceAndRetiredFalse(source).forEach(slot -> starts
                .computeIfAbsent(slot.getEvent().getId(), id -> new ArrayList<>()).add(slot.getStartsAt()));
        for (CatalogEvent event : events) {
            Scope scope = new Scope(countries.get(event.getId()), starts.getOrDefault(event.getId(), List.of()));
            applyLatest(event, observations, scope);
        }
    }

    /** Also covers historical files first published after a newer collection completed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refresh(CatalogEvent event) {
        String country = locationRepository.findById(event.getId())
                .map(CatalogEventLocation::getCountryCode).orElse(null);
        List<Instant> starts = slotRepository.findByEventOrderBySlotIndex(event).stream()
                .filter(slot -> !slot.isRetired()).map(CatalogEventTimeSlot::getStartsAt).toList();
        applyLatest(event, observations(event.getSource()), new Scope(country, starts));
    }

    private List<Observation> observations(String source) {
        return runRepository.findBySourceAndStatusOrderByStartedAtDescIdDesc(source, "COMPLETED")
                .stream()
                .map(run -> new Observation(run, new HashSet<>(run.getEventIds())))
                .toList();
    }

    private void applyLatest(CatalogEvent event, List<Observation> observations, Scope scope) {
        // Remember the latest positive observation even when a later run marked it absent.
        observations.stream()
                .filter(observation -> observation.seenIds().contains(event.getExternalId()))
                .findFirst()
                .ifPresent(observation -> event.recordSeen(observation.run().getStartedAt()));
        for (Observation observation : observations) {
            CollectionRun run = observation.run();
            Instant checkedAt = run.getStartedAt();
            // Order by collection start, never by processing or completion time.
            if (event.getSourcePresenceCheckedAt() != null
                    && !checkedAt.isAfter(event.getSourcePresenceCheckedAt())) {
                return;
            }
            if (event.getCollectedAt() != null && event.getCollectedAt().isAfter(checkedAt)) {
                return;
            }
            if (observation.seenIds().contains(event.getExternalId())) {
                event.markSeen(checkedAt, checkedAt);
                return;
            }
            if (covers(run, scope)) {
                event.markAbsent(checkedAt);
                return;
            }
        }
    }

    private boolean covers(CollectionRun run, Scope scope) {
        if (run.isFullSource()) {
            return true;
        }
        if (run.getCountryCode() == null || run.getScopeStartsAt() == null || run.getScopeEndsAt() == null) {
            // Legacy runs lack a recorded scope and must not deactivate rows.
            return false;
        }
        if (!run.getCountryCode().equalsIgnoreCase(scope.countryCode())) {
            return false;
        }
        // Unknown dates and slots outside the searched interval are deliberately left alone.
        return !scope.starts().isEmpty() && scope.starts().stream().allMatch(start -> start != null
                && !start.isBefore(run.getScopeStartsAt())
                && start.isBefore(run.getScopeEndsAt()));
    }

    private record Observation(CollectionRun run, Set<String> seenIds) {
    }

    /** The event facts a scoped run is compared against: country and active slot starts. */
    private record Scope(String countryCode, List<Instant> starts) {
    }
}
