package nl.loc.backend.user.model;

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
import org.hibernate.annotations.UuidGenerator;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "report")
public class Report {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 16)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ReportReason reason;

    @Column(length = 1000, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // set by an admin later; the member API never reads or writes these
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(length = 32)
    private String resolution;

    private Report(ReportTargetType targetType, UUID targetId, UUID reporterId, ReportReason reason, String details,
                   Instant now) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.details = details;
        // Postgres stores microseconds; truncating keeps the in-memory value equal to the stored one
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public static Report open(ReportTargetType targetType, UUID targetId, UUID reporterId, ReportReason reason,
                              String details, Instant now) {
        return new Report(targetType, targetId, reporterId, reason, details, now);
    }
}
