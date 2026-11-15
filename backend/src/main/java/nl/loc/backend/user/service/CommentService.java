package nl.loc.backend.user.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.user.dto.response.CommentAuthor;
import nl.loc.backend.user.dto.response.CommentPage;
import nl.loc.backend.user.dto.response.CommentResponse;
import nl.loc.backend.user.model.Comment;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.CommentQueryRepository;
import nl.loc.backend.user.repository.CommentRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentService {

    private final CommentQueryRepository commentQueryRepository;
    private final CommentRepository commentRepository;
    private final UserAccountRepository userAccountRepository;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public CommentService(CommentQueryRepository commentQueryRepository, CommentRepository commentRepository,
                          UserAccountRepository userAccountRepository, RateLimiter rateLimiter, Clock clock) {
        this.commentQueryRepository = commentQueryRepository;
        this.commentRepository = commentRepository;
        this.userAccountRepository = userAccountRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** viewerId is null for visitors. */
    public CommentPage comments(UUID eventId, UUID viewerId, int page, int size) {
        UUID showId = visibleShow(eventId);
        long total = commentQueryRepository.countTopLevel(showId);
        if (total == 0) {
            return new CommentPage(page, size, 0, 0, List.of());
        }
        return new CommentPage(page, size, total, (int) Math.ceil(total / (double) size),
                commentQueryRepository.findPage(showId, viewerId, page, size));
    }

    public CommentResponse createComment(UUID userId, UUID eventId, String body) {
        UserAccount author = author(userId);
        Comment comment = Comment.topLevel(visibleShow(eventId), userId, body, clock.instant());
        return response(commentRepository.save(comment), author);
    }

    public CommentResponse reply(UUID userId, UUID commentId, String body) {
        UserAccount author = author(userId);
        Comment parent = commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted() && commentQueryRepository.findVisibleShow(c.getEventId()).isPresent())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        if (parent.isReply()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reply can't be replied to.");
        }
        return response(commentRepository.save(parent.reply(userId, body, clock.instant())), author);
    }

    private UserAccount author(UUID userId) {
        // the token stays valid until it expires, even if the account was deleted in the meantime
        UserAccount author = userAccountRepository.findById(userId).orElseThrow(AccountNotFoundException::new);
        rateLimiter.consume(RateLimitPolicy.COMMENT, userId.toString());
        return author;
    }

    private UUID visibleShow(UUID eventId) {
        return commentQueryRepository.findVisibleShow(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    private static CommentResponse response(Comment comment, UserAccount author) {
        return new CommentResponse(comment.getId(), new CommentAuthor(author.getDisplayName()), comment.getBody(),
                comment.isOfficial(), false, true, comment.getCreatedAt(), null, List.of());
    }
}
