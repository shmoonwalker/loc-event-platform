package nl.loc.backend.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.user.dto.response.CommentAuthor;
import nl.loc.backend.user.dto.response.CommentResponse;
import nl.loc.backend.user.model.Comment;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.CommentQueryRepository;
import nl.loc.backend.user.repository.CommentRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccountNotFoundException;
import nl.loc.backend.user.service.AvatarStorage;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCommentService {

    private final CommentRepository commentRepository;
    private final CommentQueryRepository commentQueryRepository;
    private final UserAccountRepository userAccountRepository;
    private final AvatarStorage avatarStorage;
    private final JdbcClient jdbc;
    private final Clock clock;

    public AdminCommentService(CommentRepository commentRepository, CommentQueryRepository commentQueryRepository,
                               UserAccountRepository userAccountRepository, AvatarStorage avatarStorage,
                               JdbcClient jdbc, Clock clock) {
        this.commentRepository = commentRepository;
        this.commentQueryRepository = commentQueryRepository;
        this.userAccountRepository = userAccountRepository;
        this.avatarStorage = avatarStorage;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Removes the comment for everyone. The row stays. Deleting one that is already removed leaves who and why as
     * they were. Open comment reports are resolved either way.
     */
    @Transactional
    public void remove(UUID adminId, UUID commentId, String reason) {
        UserAccount admin = account(adminId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        comment.delete(admin.getId(), reason.trim(), now);
        resolveOpenCommentReports(commentId, admin.getId(), now);
    }

    @Transactional
    public CommentResponse officialReply(UUID adminId, UUID commentId, String body) {
        UserAccount admin = account(adminId);
        Comment parent = commentRepository.findById(commentId)
                .filter(comment -> !comment.isDeleted()
                        && commentQueryRepository.findVisibleShow(comment.getEventId()).isPresent())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        if (parent.isReply()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reply can't be replied to.");
        }
        Comment reply = commentRepository.save(parent.officialReply(admin.getId(), body, clock.instant()));
        return response(reply, admin);
    }

    private void resolveOpenCommentReports(UUID commentId, UUID adminId, Instant now) {
        jdbc.sql("""
                UPDATE app.report SET resolved_at = :now, resolved_by = :adminId, resolution = 'REMOVED'
                WHERE target_type = 'COMMENT' AND target_id = :commentId AND resolved_at IS NULL
                """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("adminId", adminId)
                .param("commentId", commentId)
                .update();
    }

    private UserAccount account(UUID userId) {
        return userAccountRepository.findById(userId).orElseThrow(AccountNotFoundException::new);
    }

    private CommentResponse response(Comment comment, UserAccount author) {
        CommentAuthor commentAuthor = new CommentAuthor(author.getDisplayName(), avatarStorage.avatarUrl(author));
        return new CommentResponse(comment.getId(), commentAuthor, comment.getBody(),
                comment.isOfficial(), false, true, comment.getCreatedAt(), comment.getEditedAt(), List.of());
    }
}
