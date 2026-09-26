package nl.loc.data.collection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "collection_run", schema = "catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRun {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private String source;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(nullable = false)
    private String status;
    @Column(name = "full_source", nullable = false)
    private boolean fullSource;
    @Column(name = "country_code")
    private String countryCode;
    @Column(name = "scope_starts_at")
    private Instant scopeStartsAt;
    @Column(name = "scope_ends_at")
    private Instant scopeEndsAt;
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_ids", nullable = false)
    private List<String> eventIds = new ArrayList<>();

    public CollectionRun(String source, Instant startedAt, CollectionScope scope) {
        this.id = UUID.randomUUID();
        this.source = source;
        this.startedAt = startedAt.truncatedTo(ChronoUnit.MICROS);
        this.status = "RUNNING";
        this.fullSource = scope.fullSource();
        this.countryCode = scope.countryCode();
        this.scopeStartsAt = scope.startsAt();
        this.scopeEndsAt = scope.endsAt();
    }

    public void complete(Instant at, List<String> ids) {
        completedAt = at;
        status = "COMPLETED";
        eventIds = new ArrayList<>(ids);
    }

    public void fail(Instant at) {
        completedAt = at;
        status = "FAILED";
    }

    public List<String> getEventIds() {
        return List.copyOf(eventIds);
    }
}
