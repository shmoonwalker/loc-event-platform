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
import nl.loc.data.event.EventImage;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_image", schema = "catalog",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "display_order"}))
public class CatalogEventImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private CatalogEvent event;

    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "ratio")
    private String ratio;

    @Column(name = "attribution")
    private String attribution;

    @Column(name = "fallback")
    private Boolean fallback;

    public CatalogEventImage(CatalogEvent event, int displayOrder, EventImage value) {
        this.event = java.util.Objects.requireNonNull(event, "event");
        this.displayOrder = displayOrder;
        updateFromImport(value);
    }

    public void updateFromImport(EventImage value) {
        this.url = value.url();
        this.width = value.width();
        this.height = value.height();
        this.ratio = value.ratio();
        this.attribution = value.attribution();
        this.fallback = value.fallback();
    }
}

