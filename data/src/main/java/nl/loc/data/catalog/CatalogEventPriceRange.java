package nl.loc.data.catalog;

import java.math.BigDecimal;
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
import nl.loc.data.event.EventPriceRange;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_price_range", schema = "catalog",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "range_index"}))
public class CatalogEventPriceRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private CatalogEvent event;

    @Column(name = "range_index", nullable = false, updatable = false)
    private int rangeIndex;

    @Column(name = "min_price")
    private BigDecimal min;

    @Column(name = "max_price")
    private BigDecimal max;

    @Column(name = "currency")
    private String currency;

    @Column(name = "price_type")
    private String type;

    public CatalogEventPriceRange(CatalogEvent event, int rangeIndex, EventPriceRange value) {
        this.event = java.util.Objects.requireNonNull(event, "event");
        this.rangeIndex = rangeIndex;
        updateFromImport(value);
    }

    public void updateFromImport(EventPriceRange value) {
        this.min = value.min();
        this.max = value.max();
        this.currency = value.currency();
        this.type = value.type();
    }
}

