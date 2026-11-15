package nl.loc.backend.user.service;

import java.util.List;
import java.util.UUID;
import nl.loc.backend.user.dto.response.CommentPage;
import nl.loc.backend.user.repository.CommentQueryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentService {

    private final CommentQueryRepository commentQueryRepository;

    public CommentService(CommentQueryRepository commentQueryRepository) {
        this.commentQueryRepository = commentQueryRepository;
    }

    /** viewerId is null for visitors. */
    public CommentPage comments(UUID eventId, UUID viewerId, int page, int size) {
        UUID showId = commentQueryRepository.findVisibleShow(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
        long total = commentQueryRepository.countTopLevel(showId);
        if (total == 0) {
            return new CommentPage(page, size, 0, 0, List.of());
        }
        return new CommentPage(page, size, total, (int) Math.ceil(total / (double) size),
                commentQueryRepository.findPage(showId, viewerId, page, size));
    }
}
