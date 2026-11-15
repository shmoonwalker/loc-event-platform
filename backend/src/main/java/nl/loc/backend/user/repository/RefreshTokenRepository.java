package nl.loc.backend.user.repository;

import jakarta.persistence.LockModeType;
import nl.loc.backend.user.model.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Lock the account first so a rotation cannot insert a successor during password change or deletion.
    @Query(value = """
            select id from app.app_user
            where id = (select user_id from app.refresh_token where token_hash = :tokenHash)
            for update
            """, nativeQuery = true)
    Optional<UUID> lockOwnerByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant now);

    // user is loaded here because the access token is built after this transaction ends
    @EntityGraph(attributePaths = "user")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeActiveByFamilyId(UUID familyId, Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpiredBefore(Instant now);
}
