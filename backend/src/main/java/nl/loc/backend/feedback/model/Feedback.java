package nl.loc.backend.feedback.model;

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
@Table(name = "feedback")
public class Feedback {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    // null for a visitor, or once the member's account is deleted
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String message;

    @Column(name = "contact_email", updatable = false, length = 254)
    private String contactEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_by")
    private UUID handledBy;

    public Feedback(UUID userId, String message, String contactEmail, Instant now) {
        this.userId = userId;
        this.message = message.trim();
        this.contactEmail = blankToNull(contactEmail);
        // Postgres stores microseconds; truncating keeps the in-memory value equal to the stored one
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** Marking it again changes nothing: the first admin and time stay. */
    public void markHandled(UUID adminId, Instant now) {
        if (handledAt != null) {
            return;
        }
        this.handledAt = now.truncatedTo(ChronoUnit.MICROS);
        this.handledBy = adminId;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
