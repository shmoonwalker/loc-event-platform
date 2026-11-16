package nl.loc.backend.user.service;

import java.util.UUID;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountDeletionService {

    private final AccountSecurityGuard guard;
    private final UserAccountRepository accounts;
    private final AvatarStorage avatarStorage;

    public AccountDeletionService(AccountSecurityGuard guard, UserAccountRepository accounts,
                                  AvatarStorage avatarStorage) {
        this.guard = guard;
        this.accounts = accounts;
        this.avatarStorage = avatarStorage;
    }

    @Transactional
    public void delete(UUID userId, String currentPassword) {
        UserAccount account = guard.requireCurrentPassword(userId, currentPassword);
        String avatarKey = account.getAvatarKey();
        // Foreign keys remove account-owned rows and clear the author of retained comments.
        accounts.delete(account);
        // only once the deletion is committed, so a rollback never leaves the account without its picture
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                avatarStorage.delete(avatarKey);
            }
        });
    }
}
