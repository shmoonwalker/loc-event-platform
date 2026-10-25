package nl.loc.backend.user.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.dto.response.EventDetail;
import nl.loc.backend.event.repository.EventDetailRepository;
import nl.loc.backend.user.dto.response.SavedEventPage;
import nl.loc.backend.user.dto.response.SavedEventResponse;
import nl.loc.backend.user.repository.SavedEventRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedEventService {

    private final SavedEventRepository savedEventRepository;
    private final UserAccountRepository userAccountRepository;
    private final EventDetailRepository eventDetailRepository;
    private final Clock clock;

    public SavedEventService(SavedEventRepository savedEventRepository, UserAccountRepository userAccountRepository,
                             EventDetailRepository eventDetailRepository, Clock clock) {
        this.savedEventRepository = savedEventRepository;
        this.userAccountRepository = userAccountRepository;
        this.eventDetailRepository = eventDetailRepository;
        this.clock = clock;
    }

    public void saveEvent(UUID userId, UUID eventId) {
        // the token stays valid until it expires, even if the account was deleted in the meantime
        if (!userAccountRepository.existsById(userId)) {
            throw new AccountNotFoundException();
        }
        if (!savedEventRepository.eventExists(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
        }
        savedEventRepository.saveEvent(userId, eventId);
    }

    public EventDetail savedEvent(UUID userId, UUID eventId) {
        return eventDetailRepository.findSavedEvent(eventId, clock.instant(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    public void unsaveEvent(UUID userId, UUID eventId) {
        savedEventRepository.deleteEvent(userId, eventId);
    }

    public SavedEventPage savedEvents(UUID userId, int page, int size) {
        long total = savedEventRepository.countSaved(userId);
        if (total == 0) {
            return new SavedEventPage(page, size, 0, 0, List.of());
        }
        List<SavedEventResponse> items = savedEventRepository.findSaved(userId, page, size);
        return new SavedEventPage(page, size, total, (int) Math.ceil(total / (double) size), items);
    }
}
