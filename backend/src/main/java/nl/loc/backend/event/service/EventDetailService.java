package nl.loc.backend.event.service;

import java.time.Clock;
import java.util.UUID;
import nl.loc.backend.event.dto.response.EventDetail;
import nl.loc.backend.event.dto.response.OrganizerDetail;
import nl.loc.backend.event.repository.EventDetailRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventDetailService {
    private final EventDetailRepository repository;
    private final Clock clock;

    public EventDetailService(EventDetailRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EventDetail event(UUID id) {
        return repository.findEvent(id, clock.instant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    public OrganizerDetail organizer(UUID id, int page, int size) {
        return repository.findOrganizer(id, clock.instant(), page, size)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer not found."));
    }
}
