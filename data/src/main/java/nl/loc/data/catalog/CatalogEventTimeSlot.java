package nl.loc.data.catalog;

import java.time.Instant;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "event_time_slot",
        schema = "catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "event_time_slot_event_index_key",
                columnNames = {"event_id", "slot_index"}
        )
)
public class CatalogEventTimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private CatalogEvent event;

    @Column(name = "slot_index", nullable = false, updatable = false)
    private int slotIndex;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    public CatalogEventTimeSlot(
            CatalogEvent event,
            int slotIndex,
            Instant startsAt,
            Instant endsAt
    ) {
        this.event = require(event, "event");
        this.slotIndex = slotIndex;
        updateFromImport(startsAt, endsAt);
    }

    public void updateFromImport(Instant startsAt, Instant endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
