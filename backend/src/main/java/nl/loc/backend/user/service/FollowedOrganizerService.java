package nl.loc.backend.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.event.dto.response.EventCard;
import nl.loc.backend.event.dto.response.EventPage;
import nl.loc.backend.event.dto.response.OrganizerInfo;
import nl.loc.backend.user.repository.FollowedOrganizerRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FollowedOrganizerService {

    private final FollowedOrganizerRepository followedOrganizerRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public FollowedOrganizerService(FollowedOrganizerRepository followedOrganizerRepository,
                                    UserAccountRepository userAccountRepository, Clock clock) {
        this.followedOrganizerRepository = followedOrganizerRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    public void followOrganizer(UUID userId, UUID organizerId) {
        if (!userAccountRepository.existsById(userId)) {
            throw new AccountNotFoundException();
        }
        if (!followedOrganizerRepository.organizerExists(organizerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer not found.");
        }
        followedOrganizerRepository.follow(userId, organizerId);
    }

    public void unfollowOrganizer(UUID userId, UUID organizerId) {
        followedOrganizerRepository.unfollow(userId, organizerId);
    }

    public List<OrganizerInfo> followedOrganizers(UUID userId) {
        return followedOrganizerRepository.findFollowed(userId);
    }

    public EventPage followedEvents(UUID userId, int page, int size) {
        // one instant for both queries, so the count and the page agree
        Instant now = clock.instant();
        long total = followedOrganizerRepository.countFollowedEvents(userId, now);
        if (total == 0) {
            return new EventPage(page, size, 0, 0, List.of());
        }
        List<EventCard> items = followedOrganizerRepository.followedEvents(userId, now, page, size);
        return new EventPage(page, size, total, (int) Math.ceil(total / (double) size), items);
    }
}
