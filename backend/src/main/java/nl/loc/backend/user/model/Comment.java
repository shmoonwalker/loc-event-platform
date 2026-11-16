package nl.loc.backend.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comment")
public class Comment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    // null once the author's account is deleted
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private boolean official;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "delete_reason")
    private String deleteReason;

    private Comment(UUID eventId, UUID parentId, UUID userId, String body, boolean official, Instant now) {
        this.eventId = eventId;
        this.parentId = parentId;
        this.userId = userId;
        this.body = body.trim();
        this.official = official;
        // Postgres stores microseconds; truncating keeps the in-memory value equal to the stored one
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public static Comment topLevel(UUID eventId, UUID userId, String body, Instant now) {
        return new Comment(eventId, null, userId, body, false, now);
    }

    public Comment reply(UUID replierId, String replyBody, Instant now) {
        return newReply(replierId, replyBody, false, now);
    }

    /** A staff reply, shown with an official badge. Same one-level rule as {@link #reply}. */
    public Comment officialReply(UUID replierId, String replyBody, Instant now) {
        return newReply(replierId, replyBody, true, now);
    }

    private Comment newReply(UUID replierId, String replyBody, boolean markOfficial, Instant now) {
        // without an id the reply would silently be stored as a top-level comment
        if (id == null) {
            throw new IllegalStateException("Save the comment before replying to it");
        }
        if (isReply()) {
            throw new IllegalStateException("A reply can't be replied to");
        }
        return new Comment(eventId, id, replierId, replyBody, markOfficial, now);
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void edit(String newBody, Instant now) {
        if (isDeleted()) {
            throw new IllegalStateException("A deleted comment can't be edited");
        }
        this.body = newBody.trim();
        this.editedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void delete(UUID deleterId, String reason, Instant now) {
        if (isDeleted()) {
            return;
        }
        this.deletedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.deletedBy = deleterId;
        this.deleteReason = reason;
    }
}
