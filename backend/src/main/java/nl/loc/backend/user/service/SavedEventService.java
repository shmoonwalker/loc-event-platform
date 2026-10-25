package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.repository.SavedEventRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedEventService {

    private final SavedEventRepository savedEventRepository;
    private final UserAccountRepository userAccountRepository;

    public SavedEventService(SavedEventRepository savedEventRepository, UserAccountRepository userAccountRepository) {
        this.savedEventRepository = savedEventRepository;
        this.userAccountRepository = userAccountRepository;
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

    public void unsaveEvent(UUID userId, UUID eventId) {
        savedEventRepository.deleteEvent(userId, eventId);
    }
}
