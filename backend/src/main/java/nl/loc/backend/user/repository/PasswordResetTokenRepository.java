package nl.loc.backend.user.repository;

import jakarta.persistence.LockModeType;
import nl.loc.backend.user.model.PasswordResetToken;
import nl.loc.backend.user.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select t.user.id from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<UUID> findOwnerByTokenHash(byte[] tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user = :user and t.usedAt is null")
    int invalidateUnused(UserAccount user, Instant now);
}
