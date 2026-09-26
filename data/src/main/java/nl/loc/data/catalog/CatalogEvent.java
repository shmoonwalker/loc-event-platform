package nl.loc.data.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nl.loc.data.event.EventLifecycle;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "event",
        schema = "catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "event_source_external_id_key",
                columnNames = {"source", "external_id"}
        )
)
public class CatalogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, updatable = false)
    private String source;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "raw_object_key", nullable = false)
    private String rawObjectKey;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "registration_url")
    private String registrationUrl;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "organizer_names", nullable = false)
    private List<String> organizerNames = new ArrayList<>();

    @Column(name = "source_created_at")
    private Instant sourceCreatedAt;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "content_hash")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private EventLifecycle lifecycleStatus;

    @Column(name = "source_active", nullable = false)
    private boolean sourceActive = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "source_presence_checked_at")
    private Instant sourcePresenceCheckedAt;

    public CatalogEvent(
            String source,
            String externalId,
            String rawObjectKey,
            String title,
            String description,
            String sourceUrl,
            String registrationUrl,
            List<String> organizerNames,
            Instant sourceCreatedAt,
            Instant sourceUpdatedAt,
            Instant collectedAt,
            EventLifecycle lifecycleStatus
    ) {
        this.source = requireText(source, "source");
        this.externalId = requireText(externalId, "externalId");
        updateFromImport(
                rawObjectKey,
                title,
                description,
                sourceUrl,
                registrationUrl,
                organizerNames,
                sourceCreatedAt,
                sourceUpdatedAt,
                collectedAt,
                lifecycleStatus
        );
    }

    public void updateFromImport(
            String rawObjectKey,
            String title,
            String description,
            String sourceUrl,
            String registrationUrl,
            List<String> organizerNames,
            Instant sourceCreatedAt,
            Instant sourceUpdatedAt,
            Instant collectedAt,
            EventLifecycle lifecycleStatus
    ) {
        this.rawObjectKey = requireText(rawObjectKey, "rawObjectKey");
        this.title = normalize(title);
        this.description = normalize(description);
        this.sourceUrl = normalize(sourceUrl);
        this.registrationUrl = normalize(registrationUrl);
        this.organizerNames = normalizeNames(organizerNames);
        this.sourceCreatedAt = sourceCreatedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.collectedAt = collectedAt;
        this.lifecycleStatus = lifecycleStatus == null ? EventLifecycle.UNKNOWN : lifecycleStatus;
    }

    public List<String> getOrganizerNames() {
        return List.copyOf(organizerNames);
    }

    public void recordContentHash(String contentHash) {
        this.contentHash = requireText(contentHash, "contentHash");
    }

    public void updateSnapshotReference(String rawObjectKey, Instant sourceCreatedAt,
                                        Instant sourceUpdatedAt, Instant collectedAt) {
        this.rawObjectKey = requireText(rawObjectKey, "rawObjectKey");
        this.sourceCreatedAt = sourceCreatedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        if (collectedAt != null && (this.collectedAt == null || collectedAt.isAfter(this.collectedAt))) {
            this.collectedAt = collectedAt;
        }
    }

    public void markSeen(Instant seenAt, Instant checkedAt) {
        sourceActive = true;
        recordSeen(seenAt);
        sourcePresenceCheckedAt = checkedAt;
    }

    public void recordSeen(Instant seenAt) {
        if (seenAt != null && (lastSeenAt == null || seenAt.isAfter(lastSeenAt))) {
            lastSeenAt = seenAt;
        }
    }

    public void markAbsent(Instant checkedAt) {
        sourceActive = false;
        sourcePresenceCheckedAt = checkedAt;
    }

    private static List<String> normalizeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String name : names) {
            String value = normalize(name);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }
}
