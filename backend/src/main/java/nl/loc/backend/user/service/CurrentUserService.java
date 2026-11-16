package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;
    private final AvatarStorage avatarStorage;

    public CurrentUserService(UserAccountRepository userAccountRepository, AvatarStorage avatarStorage) {
        this.userAccountRepository = userAccountRepository;
        this.avatarStorage = avatarStorage;
    }

    // the token stays valid until it expires, even if the account was deleted in the meantime
    public UserResponse currentUser(UUID accountId) {
        return userAccountRepository.findById(accountId)
                .map(account -> UserResponse.from(account, avatarStorage.avatarUrl(account)))
                .orElseThrow(AccountNotFoundException::new);
    }
}
