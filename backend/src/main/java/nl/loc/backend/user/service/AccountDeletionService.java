package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {

    private final AccountSecurityGuard guard;
    private final UserAccountRepository accounts;

    public AccountDeletionService(AccountSecurityGuard guard, UserAccountRepository accounts) {
        this.guard = guard;
        this.accounts = accounts;
    }

    @Transactional
    public void delete(UUID userId, String currentPassword) {
        UserAccount account = guard.requireCurrentPassword(userId, currentPassword);
        // Foreign keys remove account-owned rows and clear the author of retained comments.
        accounts.delete(account);
    }
}
