package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserAccountRepository userAccountRepository;

    public ProfileService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserResponse changeDisplayName(UUID accountId, String displayName) {
        UserAccount account = account(accountId);
        account.changeDisplayName(displayName);
        return UserResponse.from(account);
    }

    // the token stays valid until it expires, even if the account was deleted in the meantime
    private UserAccount account(UUID accountId) {
        return userAccountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }
}
