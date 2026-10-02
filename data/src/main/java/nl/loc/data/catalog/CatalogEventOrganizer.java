package nl.loc.data.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nl.loc.data.event.EventOrganizer;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_organizer", schema = "catalog",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"event_id", "organizer_index"}),
                @UniqueConstraint(columnNames = {"event_id", "external_id"})
        })
public class CatalogEventOrganizer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private CatalogEvent event;

    @Column(name = "organizer_index", nullable = false)
    private int organizerIndex;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "site")
    private String site;

    public CatalogEventOrganizer(CatalogEvent event, int organizerIndex, EventOrganizer value) {
        this.event = java.util.Objects.requireNonNull(event, "event");
        updateFromImport(organizerIndex, value);
    }

    public void updateFromImport(int organizerIndex, EventOrganizer value) {
        java.util.Objects.requireNonNull(value, "value");
        this.organizerIndex = organizerIndex;
        this.externalId = requireText(value.externalId(), "externalId");
        this.name = requireText(value.name(), "name");
        this.description = normalize(value.description());
        this.site = normalize(value.site());
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
