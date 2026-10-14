package nl.loc.backend.user.repository;

import nl.loc.backend.user.model.EmailVerificationToken;
import nl.loc.backend.user.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("""
        update EmailVerificationToken t set t.usedAt = :now
        where t.user = :user and t.usedAt is null
        """)
    int invalidateUnused(UserAccount user, Instant now);
}
