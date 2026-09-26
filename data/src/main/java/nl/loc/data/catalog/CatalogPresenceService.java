package nl.loc.data.catalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
        for (CatalogEvent event : eventRepository.findBySource(source)) {
            applyLatest(event, observations);
        }
    }

    /** Also covers historical files first published after a newer collection completed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refresh(CatalogEvent event) {
        applyLatest(event, observations(event.getSource()));
    }

    private List<Observation> observations(String source) {
        return runRepository.findBySourceAndStatusOrderByStartedAtDescIdDesc(source, "COMPLETED")
                .stream()
                .map(run -> new Observation(run, new HashSet<>(run.getEventIds())))
                .toList();
    }

    private void applyLatest(CatalogEvent event, List<Observation> observations) {
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
            if (covers(run, event)) {
                event.markAbsent(checkedAt);
                return;
            }
        }
    }

    private boolean covers(CollectionRun run, CatalogEvent event) {
        if (run.isFullSource()) {
            return true;
        }
        if (run.getCountryCode() == null || run.getScopeStartsAt() == null || run.getScopeEndsAt() == null) {
            // Legacy runs lack a recorded scope and must not deactivate rows.
            return false;
        }
        boolean sameCountry = locationRepository.findById(event.getId())
                .map(location -> run.getCountryCode().equalsIgnoreCase(location.getCountryCode()))
                .orElse(false);
        if (!sameCountry) {
            return false;
        }
        List<CatalogEventTimeSlot> slots = slotRepository.findByEventOrderBySlotIndex(event);
        // Unknown dates and slots outside the searched interval are deliberately left alone.
        return !slots.isEmpty() && slots.stream().allMatch(slot -> slot.getStartsAt() != null
                && !slot.getStartsAt().isBefore(run.getScopeStartsAt())
                && slot.getStartsAt().isBefore(run.getScopeEndsAt()));
    }

    private record Observation(CollectionRun run, Set<String> seenIds) {
    }
}
