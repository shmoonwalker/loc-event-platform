package nl.loc.data.catalog;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_category", schema = "catalog")
public class CatalogEventCategory {
    @EmbeddedId
    private CatalogEventCategoryKey id;

    @ManyToOne(optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id", nullable = false)
    private CatalogEvent event;

    @ManyToOne(optional = false)
    @MapsId("categoryId")
    @JoinColumn(name = "category_id", nullable = false)
    private CatalogCategory category;

    public CatalogEventCategory(CatalogEvent event, CatalogCategory category) {
        this.event = java.util.Objects.requireNonNull(event, "event");
        this.category = java.util.Objects.requireNonNull(category, "category");
        this.id = new CatalogEventCategoryKey(event.getId(), category.getId());
    }
}
