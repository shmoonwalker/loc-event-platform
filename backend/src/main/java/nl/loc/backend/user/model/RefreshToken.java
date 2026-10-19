package nl.loc.backend.user.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;
    @Column(name = "token_hash", nullable = false, unique = true)
    private byte[] tokenHash;
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "replaced_by")
    private UUID replacedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(UserAccount user, byte[] tokenHash, UUID familyId, Instant createdAt, Duration ttl) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        this.expiresAt = this.createdAt.plus(ttl);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && replacedBy == null && now.isBefore(expiresAt);
    }
    public void revoke(Instant now) {
        revokedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
    public void markReplacedBy(UUID successorId) {
        this.replacedBy = successorId;
    }


}
