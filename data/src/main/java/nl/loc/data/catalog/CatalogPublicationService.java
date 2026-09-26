package nl.loc.data.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.event.EventImage;
import nl.loc.data.event.EventLocation;
import nl.loc.data.event.EventPriceRange;
import nl.loc.data.event.EventTimeSlot;
import nl.loc.data.event.NormalizedEvent;
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
    private final CatalogEventPriceRangeRepository catalogEventPriceRangeRepository;
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

    private void upsertLocation(CatalogEvent catalogEvent, EventLocation location) {
        Optional<CatalogEventLocation> existing = catalogEventLocationRepository.findById(catalogEvent.getId());

        if (location == null) {
            existing.ifPresent(catalogLocation -> {
                catalogEventLocationRepository.delete(catalogLocation);
                log.debug("Scheduled removal of absent location catalogEventId={}", catalogEvent.getId());
            });
            return;
        }

        CatalogEventLocation catalogLocation;
        if (existing.isPresent()) {
            catalogLocation = existing.get();
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
    }

    private void syncTimeSlots(CatalogEvent catalogEvent, List<EventTimeSlot> slots) {
        List<EventTimeSlot> newSlots = slots == null ? List.of() : slots;
        List<CatalogEventTimeSlot> existingSlots = catalogEventTimeSlotRepository.findByEventOrderBySlotIndex(catalogEvent);

        Map<Integer, CatalogEventTimeSlot> existingByIndex = new HashMap<>();
        for (CatalogEventTimeSlot existingSlot : existingSlots) {
            existingByIndex.put(existingSlot.getSlotIndex(), existingSlot);
        }

        List<CatalogEventTimeSlot> slotsToSave = new ArrayList<>();
        for (int i = 0; i < newSlots.size(); i++) {
            EventTimeSlot slot = newSlots.get(i);
            CatalogEventTimeSlot catalogSlot = existingByIndex.get(i);

            if (catalogSlot != null) {
                catalogSlot.updateFromImport(slot);
            } else {
                catalogSlot = new CatalogEventTimeSlot(catalogEvent, i, slot);
            }

            slotsToSave.add(catalogSlot);
        }
        catalogEventTimeSlotRepository.saveAll(slotsToSave);

        List<CatalogEventTimeSlot> slotsToDelete = new ArrayList<>();
        for (CatalogEventTimeSlot existingSlot : existingSlots) {
            if (existingSlot.getSlotIndex() >= newSlots.size()) {
                slotsToDelete.add(existingSlot);
            }
        }
        catalogEventTimeSlotRepository.deleteAll(slotsToDelete);
        log.debug("Scheduled catalog time slot changes catalogEventId={} saved={} deleted={}",
                catalogEvent.getId(), slotsToSave.size(), slotsToDelete.size());
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

    private void syncPriceRanges(CatalogEvent event, List<EventPriceRange> ranges) {
        List<EventPriceRange> incoming = ranges == null ? List.of() : ranges;
        List<CatalogEventPriceRange> existing = catalogEventPriceRangeRepository
                .findByEventOrderByRangeIndex(event);
        Map<Integer, CatalogEventPriceRange> byIndex = new HashMap<>();
        for (CatalogEventPriceRange row : existing) {
            byIndex.put(row.getRangeIndex(), row);
        }
        List<CatalogEventPriceRange> toSave = new ArrayList<>();
        for (int index = 0; index < incoming.size(); index++) {
            CatalogEventPriceRange row = byIndex.get(index);
            if (row == null) {
                row = new CatalogEventPriceRange(event, index, incoming.get(index));
            } else {
                row.updateFromImport(incoming.get(index));
            }
            toSave.add(row);
        }
        catalogEventPriceRangeRepository.saveAll(toSave);
        catalogEventPriceRangeRepository.deleteAll(existing.stream()
                .filter(row -> row.getRangeIndex() >= incoming.size())
                .toList());
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
        upsertLocation(catalogEvent, event.location());
        syncTimeSlots(catalogEvent, event.timeSlots());
        syncImages(catalogEvent, event.images());
        syncPriceRanges(catalogEvent, event.priceRanges());
        // Flush child changes before presence checks query the event's country and dates.
        catalogEventRepository.flush();
        presenceService.refresh(catalogEvent);
        return catalogEvent;
    }
}
