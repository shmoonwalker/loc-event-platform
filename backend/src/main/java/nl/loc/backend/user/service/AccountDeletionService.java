package nl.loc.backend.user.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.repository.AdminReportRepository;
import nl.loc.backend.photo.model.PhotoVariant;
import nl.loc.backend.photo.repository.EventPhotoRepository;
import nl.loc.backend.photo.service.EventPhotoStorage;
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
    private final EventPhotoRepository eventPhotoRepository;
    private final EventPhotoStorage eventPhotoStorage;
    private final AdminReportRepository adminReportRepository;
    private final Clock clock;

    public AccountDeletionService(AccountSecurityGuard guard, UserAccountRepository accounts,
                                  AvatarStorage avatarStorage, EventPhotoRepository eventPhotoRepository,
                                  EventPhotoStorage eventPhotoStorage, AdminReportRepository adminReportRepository,
                                  Clock clock) {
        this.guard = guard;
        this.accounts = accounts;
        this.avatarStorage = avatarStorage;
        this.eventPhotoRepository = eventPhotoRepository;
        this.eventPhotoStorage = eventPhotoStorage;
        this.adminReportRepository = adminReportRepository;
        this.clock = clock;
    }

    @Transactional
    public void delete(UUID userId, String currentPassword) {
        UserAccount account = guard.requireCurrentPassword(userId, currentPassword);
        String avatarKey = account.getAvatarKey();
        List<UUID> photoIds = eventPhotoRepository.findIdsByUploaderId(userId);
        OffsetDateTime resolvedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (UUID photoId : photoIds) {
            adminReportRepository.resolveOpenPhotoReports(photoId, userId, resolvedAt);
        }
        // Foreign keys remove account-owned rows and clear the author of retained comments.
        accounts.delete(account);
        // only once the deletion is committed, so a rollback never leaves the account without its files
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                avatarStorage.delete(avatarKey);
                for (UUID photoId : photoIds) {
                    eventPhotoStorage.delete(EventPhotoStorage.key(photoId, PhotoVariant.CARD));
                    eventPhotoStorage.delete(EventPhotoStorage.key(photoId, PhotoVariant.FULL));
                }
            }
        });
    }
}
