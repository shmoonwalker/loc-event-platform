package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    // the token stays valid until it expires, even if the account was deleted in the meantime
    public UserResponse currentUser(UUID accountId) {
        return userAccountRepository.findById(accountId)
                .map(UserResponse::from)
                .orElseThrow(AccountNotFoundException::new);
    }
}
