package nl.loc.backend.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.dto.HiddenEventResponse;
import nl.loc.backend.admin.repository.HiddenEventRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HiddenEventService {

    private final HiddenEventRepository hiddenEventRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public HiddenEventService(HiddenEventRepository hiddenEventRepository,
                              UserAccountRepository userAccountRepository, Clock clock) {
        this.hiddenEventRepository = hiddenEventRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public void hide(UUID adminId, UUID eventId, String reason) {
        requireAccount(adminId);
        hiddenEventRepository.hide(showId(eventId), adminId, reason.trim(), now());
    }

    /** Does not reopen reports closed when the event was hidden. */
    @Transactional
    public void unhide(UUID adminId, UUID eventId) {
        requireAccount(adminId);
        hiddenEventRepository.unhide(showId(eventId));
    }

    @Transactional(readOnly = true)
    public List<HiddenEventResponse> hiddenEvents() {
        return hiddenEventRepository.list();
    }

    private void requireAccount(UUID adminId) {
        if (!userAccountRepository.existsById(adminId)) {
            throw new AccountNotFoundException();
        }
    }

    private UUID showId(UUID eventId) {
        return hiddenEventRepository.findShowId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
