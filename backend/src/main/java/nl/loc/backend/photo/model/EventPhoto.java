package nl.loc.backend.photo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_photo")
public class EventPhoto {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PhotoStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private EventPhoto(UUID id, UUID eventId, UUID uploaderId, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.uploaderId = uploaderId;
        this.status = PhotoStatus.PENDING;
        // Postgres stores microseconds; truncating keeps the in-memory value equal to the stored one
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    public static EventPhoto pending(UUID id, UUID eventId, UUID uploaderId, Instant createdAt) {
        return new EventPhoto(id, eventId, uploaderId, createdAt);
    }

    public void mark(PhotoStatus next) {
        this.status = next;
    }
}
