package nl.loc.backend.user.service;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.model.AvatarPreset;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

// not @Transactional: R2 objects are deleted only after the account change is committed
@Slf4j
@Service
public class ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final AvatarImages avatarImages;
    private final AvatarStorage avatarStorage;

    public ProfileService(UserAccountRepository userAccountRepository, AvatarImages avatarImages,
                          AvatarStorage avatarStorage) {
        this.userAccountRepository = userAccountRepository;
        this.avatarImages = avatarImages;
        this.avatarStorage = avatarStorage;
    }

    public UserResponse changeDisplayName(UUID accountId, String displayName) {
        UserAccount account = account(accountId);
        account.changeDisplayName(displayName);
        return save(account);
    }

    public UserResponse choosePreset(UUID accountId, AvatarPreset preset) {
        UserAccount account = account(accountId);
        String removedKey = account.choosePreset(preset);
        UserResponse response = save(account);
        avatarStorage.delete(removedKey);
        return response;
    }

    // shortcut: two uploads at the same moment can leave one unused object in R2; harmless, rate limited
    public UserResponse uploadAvatar(UUID accountId, byte[] upload) {
        UserAccount account = account(accountId);
        byte[] jpeg = avatarImages.toAvatarJpeg(upload);
        // a new key per upload, so browsers and the CDN never show an old picture
        String key = "avatars/" + accountId + "/" + UUID.randomUUID() + ".jpg";
        avatarStorage.put(key, jpeg);
        String replacedKey = account.useUploadedAvatar(key);
        UserResponse response;
        try {
            response = save(account);
        } catch (RuntimeException ex) {
            avatarStorage.delete(key);
            throw ex;
        }
        avatarStorage.delete(replacedKey);
        log.info("Stored uploaded avatar for account {}", accountId);
        return response;
    }

    public UserResponse removeUploadedAvatar(UUID accountId) {
        UserAccount account = account(accountId);
        String removedKey = account.removeUploadedAvatar();
        UserResponse response = save(account);
        avatarStorage.delete(removedKey);
        return response;
    }

    private UserResponse save(UserAccount account) {
        UserAccount saved = userAccountRepository.save(account);
        return UserResponse.from(saved, avatarStorage.avatarUrl(saved));
    }

    // the token stays valid until it expires, even if the account was deleted in the meantime
    private UserAccount account(UUID accountId) {
        return userAccountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }
}
