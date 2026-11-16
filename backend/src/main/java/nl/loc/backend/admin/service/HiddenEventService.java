package nl.loc.backend.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.dto.HiddenEventResponse;
import nl.loc.backend.admin.repository.AdminReportRepository;
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
    private final AdminReportRepository adminReportRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public HiddenEventService(HiddenEventRepository hiddenEventRepository, AdminReportRepository adminReportRepository,
                              UserAccountRepository userAccountRepository, Clock clock) {
        this.hiddenEventRepository = hiddenEventRepository;
        this.adminReportRepository = adminReportRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    /** Also closes open EVENT reports of the show. Hiding again keeps the first row and closes any new ones. */
    @Transactional
    public void hide(UUID adminId, UUID eventId, String reason) {
        requireAccount(adminId);
        UUID showId = showId(eventId);
        OffsetDateTime hiddenAt = now();
        hiddenEventRepository.hide(showId, adminId, reason.trim(), hiddenAt);
        adminReportRepository.resolveOpenEventReports(showId, adminId, hiddenAt);
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
