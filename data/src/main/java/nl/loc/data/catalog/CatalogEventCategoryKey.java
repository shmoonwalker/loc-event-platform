package nl.loc.data.catalog;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class CatalogEventCategoryKey implements Serializable {
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    public CatalogEventCategoryKey(Long eventId, Long categoryId) {
        this.eventId = eventId;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogEventCategoryKey key)) {
            return false;
        }
        return Objects.equals(eventId, key.eventId) && Objects.equals(categoryId, key.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, categoryId);
    }
}
