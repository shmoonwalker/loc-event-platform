package nl.loc.backend.user.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_user")
public class UserAccount {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_preset", nullable = false)
    private AvatarPreset avatarPreset;

    // object key of the uploaded picture in avatar storage; null shows the preset
    @Column(name = "avatar_key")
    private String avatarKey;

    public UserAccount(String email, String displayName, String passwordHash, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.displayName = displayName.trim();
        this.passwordHash = passwordHash;
        this.role = Role.USER;
        this.avatarPreset = AvatarPreset.random();
        // Postgres stores microseconds; truncating keeps the in-memory value equal to the stored one
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void changeDisplayName(String newDisplayName) {
        this.displayName = newDisplayName.trim();
    }

    /** Also removes the uploaded picture, so the chosen preset shows; returns the removed key. */
    public String choosePreset(AvatarPreset preset) {
        this.avatarPreset = preset;
        return removeUploadedAvatar();
    }

    /** Returns the key of the picture it replaces, or null. */
    public String useUploadedAvatar(String key) {
        String previous = avatarKey;
        avatarKey = key;
        return previous;
    }

    /** Returns the removed key, or null. */
    public String removeUploadedAvatar() {
        return useUploadedAvatar(null);
    }

    // latest registration wins, so an attacker who registered this email first can't keep their password
    public void replaceRegistration(String replacementPasswordHash, String replacementDisplayName) {
        if (isEmailVerified()) {
            throw new IllegalStateException("A verified account's registration can't be replaced");
        }
        this.passwordHash = replacementPasswordHash;
        this.displayName = replacementDisplayName.trim();
    }
}
