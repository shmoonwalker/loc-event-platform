package nl.loc.data.catalog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.event.Category;
import nl.loc.data.event.CategoryAssignment;
import nl.loc.data.event.EventImage;
import nl.loc.data.event.EventLocation;
import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.NormalizedEvent;
import nl.loc.data.tagging.TagRepository;
import nl.loc.data.weather.WeatherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CatalogPublicationService {

    private final CatalogEventTimeSlotRepository catalogEventTimeSlotRepository;
    private final CatalogEventLocationRepository catalogEventLocationRepository;
    private final CatalogEventRepository catalogEventRepository;
    private final CatalogEventImageRepository catalogEventImageRepository;
    private final CatalogCategoryRepository catalogCategoryRepository;
    private final CatalogEventCategoryRepository catalogEventCategoryRepository;
    private final WeatherRepository weatherRepository;
    private final TagRepository tagRepository;
    private final CatalogSourceLock sourceLock;
    private final CatalogPresenceService presenceService;

    private CatalogEvent upsertEvent(NormalizedEvent event, Instant collectedAt, Optional<CatalogEvent> existing) {
        CatalogEvent catalogEvent;
        if (existing.isPresent()) {
            catalogEvent = existing.get();
            catalogEvent.updateFromImport(
                    event.rawObjectKey(),
                    event.title(),
                    event.description(),
                    event.sourceUrl(),
                    event.registrationUrl(),
                    event.organizerNames(),
                    event.sourceCreatedAt(),
                    event.sourceUpdatedAt(),
                    collectedAt,
                    event.lifecycleStatus()
            );
        } else {
            catalogEvent = new CatalogEvent(
                    event.source(),
                    event.externalId(),
                    event.rawObjectKey(),
                    event.title(),
                    event.description(),
                    event.sourceUrl(),
                    event.registrationUrl(),
                    event.organizerNames(),
                    event.sourceCreatedAt(),
                    event.sourceUpdatedAt(),
                    collectedAt,
                    event.lifecycleStatus()
            );
        }

        log.debug("Saving catalog event source={} externalId={} operation={}",
                event.source(), event.externalId(), existing.isPresent() ? "update" : "insert");
        return catalogEventRepository.save(catalogEvent);
    }

    /** Reports whether the place moved, which makes any forecast stored for this event wrong. */
    private boolean upsertLocation(CatalogEvent catalogEvent, EventLocation location) {
        Optional<CatalogEventLocation> existing = catalogEventLocationRepository.findById(catalogEvent.getId());

        if (location == null) {
            if (existing.isEmpty()) {
                return false;
            }
            catalogEventLocationRepository.delete(existing.get());
            log.debug("Scheduled removal of absent location catalogEventId={}", catalogEvent.getId());
            return true;
        }

        CatalogEventLocation catalogLocation;
        boolean moved = false;
        if (existing.isPresent()) {
            catalogLocation = existing.get();
            moved = !Objects.equals(catalogLocation.getLatitude(), location.latitude())
                    || !Objects.equals(catalogLocation.getLongitude(), location.longitude())
                    || catalogLocation.getLocationType() != location.locationType();
            catalogLocation.updateFromImport(
                    location.locationType(),
                    location.venueName(),
                    location.address(),
                    location.city(),
                    location.postalCode(),
                    location.country(),
                    location.latitude(),
                    location.longitude(),
                    location.externalVenueId(),
                    location.countryCode()
            );
        } else {
            catalogLocation = new CatalogEventLocation(
                    catalogEvent,
                    location.locationType(),
                    location.venueName(),
                    location.address(),
                    location.city(),
                    location.postalCode(),
                    location.country(),
                    location.latitude(),
                    location.longitude(),
                    location.externalVenueId(),
                    location.countryCode()
            );
        }

        log.debug("Saving catalog location catalogEventId={} operation={}",
                catalogEvent.getId(), existing.isPresent() ? "update" : "insert");
        catalogEventLocationRepository.save(catalogLocation);
        return moved;
    }

    private SlotSync syncTimeSlots(CatalogEvent catalogEvent, List<EventTimeSlot> slots) {
        List<EventTimeSlot> newSlots = slots == null ? List.of() : slots;
        List<CatalogEventTimeSlot> existingSlots = catalogEventTimeSlotRepository.findByEventOrderBySlotIndex(catalogEvent);

        Map<Integer, CatalogEventTimeSlot> existingByIndex = new HashMap<>();
        for (CatalogEventTimeSlot existingSlot : existingSlots) {
            existingByIndex.put(existingSlot.getSlotIndex(), existingSlot);
        }

        List<CatalogEventTimeSlot> slotsToSave = new ArrayList<>();
        Set<Long> rescheduledSlotIds = new HashSet<>();
        for (int i = 0; i < newSlots.size(); i++) {
            EventTimeSlot slot = newSlots.get(i);
            CatalogEventTimeSlot catalogSlot = existingByIndex.get(i);

            if (catalogSlot != null) {
                // Rows are matched by position, so a reused row can end up describing another date.
                if (!sameHour(catalogSlot.getStartsAt(), slot.startsAt())) {
                    rescheduledSlotIds.add(catalogSlot.getId());
                }
                catalogSlot.updateFromImport(slot);
            } else {
                catalogSlot = new CatalogEventTimeSlot(catalogEvent, i, slot);
            }

            slotsToSave.add(catalogSlot);
        }
        List<CatalogEventTimeSlot> savedSlots = catalogEventTimeSlotRepository.saveAll(slotsToSave);

        List<CatalogEventTimeSlot> slotsToDelete = new ArrayList<>();
        for (CatalogEventTimeSlot existingSlot : existingSlots) {
            if (existingSlot.getSlotIndex() >= newSlots.size()) {
                slotsToDelete.add(existingSlot);
            }
        }
        catalogEventTimeSlotRepository.deleteAll(slotsToDelete);
        log.debug("Scheduled catalog time slot changes catalogEventId={} saved={} deleted={}",
                catalogEvent.getId(), slotsToSave.size(), slotsToDelete.size());
        return new SlotSync(
                savedSlots.stream().map(CatalogEventTimeSlot::getId).toList(),
                rescheduledSlotIds);
    }

    /** A forecast describes one hour in one place, so a change to either makes it wrong to serve. */
    private void clearOutdatedWeather(CatalogEvent catalogEvent, SlotSync slotSync, boolean locationMoved) {
        Collection<Long> timeSlotIds = locationMoved ? slotSync.savedSlotIds() : slotSync.rescheduledSlotIds();
        int cleared = weatherRepository.deleteForTimeSlots(timeSlotIds);
        if (cleared > 0) {
            log.info("Cleared stored forecasts catalogEventId={} timeSlots={} reason={}",
                    catalogEvent.getId(), cleared, locationMoved ? "location moved" : "slot rescheduled");
        }
    }

    private static boolean sameHour(Instant previous, Instant current) {
        if (previous == null || current == null) {
            return previous == current;
        }
        return previous.truncatedTo(ChronoUnit.HOURS).equals(current.truncatedTo(ChronoUnit.HOURS));
    }

    private record SlotSync(List<Long> savedSlotIds, Set<Long> rescheduledSlotIds) {
    }

    private void syncImages(CatalogEvent event, List<EventImage> images) {
        List<EventImage> incoming = images == null ? List.of() : images;
        List<CatalogEventImage> existing = catalogEventImageRepository
                .findByEventOrderByDisplayOrder(event);
        Map<Integer, CatalogEventImage> byIndex = new HashMap<>();
        for (CatalogEventImage row : existing) {
            byIndex.put(row.getDisplayOrder(), row);
        }
        List<CatalogEventImage> toSave = new ArrayList<>();
        for (int index = 0; index < incoming.size(); index++) {
            CatalogEventImage row = byIndex.get(index);
            if (row == null) {
                row = new CatalogEventImage(event, index, incoming.get(index));
            } else {
                row.updateFromImport(incoming.get(index));
            }
            toSave.add(row);
        }
        catalogEventImageRepository.saveAll(toSave);
        catalogEventImageRepository.deleteAll(existing.stream()
                .filter(row -> row.getDisplayOrder() >= incoming.size())
                .toList());
    }

    private void syncCategories(CatalogEvent event, List<Category> categories) {
        List<Category> incoming = CategoryAssignment.resolve(categories);
        Map<String, CatalogCategory> storedByName = new HashMap<>();
        for (CatalogCategory stored : catalogCategoryRepository.findAll()) {
            storedByName.put(stored.getName(), stored);
        }
        List<CatalogCategory> wanted = new ArrayList<>();
        for (Category category : incoming) {
            CatalogCategory stored = storedByName.get(category.catalogName());
            if (stored == null) {
                throw new IllegalStateException("Catalog category is missing: " + category.catalogName());
            }
            wanted.add(stored);
        }
        List<CatalogEventCategory> existing = catalogEventCategoryRepository.findByEvent(event);
        Set<Long> wantedIds = new HashSet<>();
        for (CatalogCategory category : wanted) {
            wantedIds.add(category.getId());
        }
        catalogEventCategoryRepository.deleteAll(existing.stream()
                .filter(link -> !wantedIds.contains(link.getCategory().getId()))
                .toList());
        Set<Long> existingIds = new HashSet<>();
        for (CatalogEventCategory link : existing) {
            existingIds.add(link.getCategory().getId());
        }
        List<CatalogEventCategory> toSave = new ArrayList<>();
        for (CatalogCategory category : wanted) {
            if (!existingIds.contains(category.getId())) {
                toSave.add(new CatalogEventCategory(event, category));
            }
        }
        catalogEventCategoryRepository.saveAll(toSave);
    }

    @Transactional
    public CatalogEvent publish(NormalizedEvent event, Instant collectedAt, String contentHash) {
        sourceLock.acquire(event.source());
        log.debug("Publishing catalog event source={} externalId={} rawObjectKey={}",
                event.source(), event.externalId(), event.rawObjectKey());
        Optional<CatalogEvent> existing = catalogEventRepository.findBySourceAndExternalId(
                event.source(),
                event.externalId()
        );
        if (existing.isPresent()) {
            CatalogEvent storedEvent = existing.get();
            if (storedEvent.getSourceUpdatedAt() != null
                    && (event.sourceUpdatedAt() == null
                    || event.sourceUpdatedAt().isBefore(storedEvent.getSourceUpdatedAt()))) {
                log.info("Skipping outdated snapshot source={} externalId={} incomingSourceUpdatedAt={} storedSourceUpdatedAt={} rawObjectKey={}",
                        event.source(), event.externalId(), event.sourceUpdatedAt(),
                        storedEvent.getSourceUpdatedAt(), event.rawObjectKey());
                return storedEvent;
            }
            // Collection order is only a fallback when neither snapshot has a source update time.
            if (storedEvent.getSourceUpdatedAt() == null && event.sourceUpdatedAt() == null
                    && storedEvent.getCollectedAt() != null
                    && (collectedAt == null || collectedAt.isBefore(storedEvent.getCollectedAt()))) {
                log.info("Skipping outdated collection source={} externalId={} incomingCollectedAt={} storedCollectedAt={} rawObjectKey={}",
                        event.source(), event.externalId(), collectedAt,
                        storedEvent.getCollectedAt(), event.rawObjectKey());
                return storedEvent;
            }
            if (contentHash.equals(storedEvent.getContentHash())) {
                // Freshness must advance even when content is identical; otherwise an
                // intermediate older snapshot could later restore outdated content.
                storedEvent.updateSnapshotReference(event.rawObjectKey(), event.sourceCreatedAt(),
                        event.sourceUpdatedAt(), collectedAt);
                presenceService.refresh(storedEvent);
                log.info("Skipping unchanged event content source={} externalId={} rawObjectKey={}",
                        event.source(), event.externalId(), event.rawObjectKey());
                return storedEvent;
            }
        }

        CatalogEvent catalogEvent = upsertEvent(event, collectedAt, existing);
        catalogEvent.recordContentHash(contentHash);
        boolean locationMoved = upsertLocation(catalogEvent, event.location());
        SlotSync slotSync = syncTimeSlots(catalogEvent, event.timeSlots());
        clearOutdatedWeather(catalogEvent, slotSync, locationMoved);
        syncImages(catalogEvent, event.images());
        syncCategories(catalogEvent, event.categories());
        tagRepository.invalidate(catalogEvent.getId());
        // Flush child changes before presence checks query the event's country and dates.
        catalogEventRepository.flush();
        presenceService.refresh(catalogEvent);
        return catalogEvent;
    }
}
