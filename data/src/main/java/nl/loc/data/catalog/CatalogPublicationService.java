package nl.loc.data.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.event.EventLocation;
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

    private CatalogEvent upsertEvent(NormalizedEvent event, Optional<CatalogEvent> existing) {
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
                    event.sourceUpdatedAt()
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
                    event.sourceUpdatedAt()
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
                    location.longitude()
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
                    location.longitude()
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
                catalogSlot.updateFromImport(slot.startsAt(), slot.endsAt());
            } else {
                catalogSlot = new CatalogEventTimeSlot(catalogEvent, i, slot.startsAt(), slot.endsAt());
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

    @Transactional
    public CatalogEvent publish(NormalizedEvent event) {
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
        }

        CatalogEvent catalogEvent = upsertEvent(event, existing);
        upsertLocation(catalogEvent, event.location());
        syncTimeSlots(catalogEvent, event.timeSlots());
        return catalogEvent;
    }
}
