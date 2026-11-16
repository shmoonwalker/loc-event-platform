package nl.loc.backend.photo.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.admin.repository.AdminReportRepository;
import nl.loc.backend.photo.dto.EventPhotoResponse;
import nl.loc.backend.photo.dto.PhotoUploader;
import nl.loc.backend.photo.model.EventPhoto;
import nl.loc.backend.photo.model.PhotoStatus;
import nl.loc.backend.photo.model.PhotoVariant;
import nl.loc.backend.photo.repository.EventPhotoQueryRepository;
import nl.loc.backend.photo.repository.EventPhotoQueryRepository.PhotoView;
import nl.loc.backend.photo.repository.EventPhotoRepository;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class EventPhotoService {

    public static final int MAX_PER_EVENT = 10;
    private static final int ADMIN_LIMIT = 50;
    private static final Set<PhotoStatus> COUNTED = Set.of(PhotoStatus.PENDING, PhotoStatus.APPROVED);

    private final EventPhotoRepository eventPhotoRepository;
    private final EventPhotoQueryRepository eventPhotoQueryRepository;
    private final UserAccountRepository userAccountRepository;
    private final AdminReportRepository adminReportRepository;
    private final EventPhotoImages eventPhotoImages;
    private final EventPhotoStorage eventPhotoStorage;
    private final EventPhotoUrls eventPhotoUrls;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EventPhotoService(EventPhotoRepository eventPhotoRepository,
                             EventPhotoQueryRepository eventPhotoQueryRepository,
                             UserAccountRepository userAccountRepository,
                             AdminReportRepository adminReportRepository,
                             EventPhotoImages eventPhotoImages,
                             EventPhotoStorage eventPhotoStorage,
                             EventPhotoUrls eventPhotoUrls,
                             JdbcClient jdbc,
                             TransactionTemplate transactionTemplate,
                             Clock clock) {
        this.eventPhotoRepository = eventPhotoRepository;
        this.eventPhotoQueryRepository = eventPhotoQueryRepository;
        this.userAccountRepository = userAccountRepository;
        this.adminReportRepository = adminReportRepository;
        this.eventPhotoImages = eventPhotoImages;
        this.eventPhotoStorage = eventPhotoStorage;
        this.eventPhotoUrls = eventPhotoUrls;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public EventPhotoResponse upload(UUID userId, UUID eventId, byte[] file) {
        if (file.length > EventPhotoImages.MAX_BYTES) {
            throw new MaxUploadSizeExceededException(EventPhotoImages.MAX_BYTES);
        }
        UserAccount uploader = requireUser(userId);
        UUID showId = openShow(eventId);
        if (openCount(uploader.getId(), showId) >= MAX_PER_EVENT) {
            throw tooMany();
        }
        EventPhotoImages.Derivatives derivatives = eventPhotoImages.derivatives(file);
        UUID id = newId();
        String cardKey = EventPhotoStorage.key(id, PhotoVariant.CARD);
        String fullKey = EventPhotoStorage.key(id, PhotoVariant.FULL);
        eventPhotoStorage.put(cardKey, derivatives.card());
        try {
            eventPhotoStorage.put(fullKey, derivatives.full());
            EventPhotoResponse saved = transactionTemplate.execute(status -> insert(id, showId, uploader));
            log.info("Stored event photo {} for event {}", id, showId);
            return Objects.requireNonNull(saved);
        } catch (RuntimeException ex) {
            eventPhotoStorage.delete(cardKey);
            eventPhotoStorage.delete(fullKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<EventPhotoResponse> approved(UUID eventId) {
        UUID showId = openShow(eventId);
        return eventPhotoQueryRepository.approved(showId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<EventPhotoResponse> own(UUID userId, UUID eventId) {
        requireUser(userId);
        UUID showId = openShow(eventId);
        return eventPhotoQueryRepository.own(showId, userId).stream().map(this::response).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID eventId, UUID photoId) {
        requireUser(userId);
        UUID showId = openShow(eventId);
        EventPhoto photo = photoOnShow(photoId, showId);
        if (!photo.getUploaderId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own photo.");
        }
        remove(photo, userId);
    }

    @Transactional(readOnly = true)
    public PhotoFile file(UUID eventId, UUID photoId, PhotoVariant variant, Jwt jwt) {
        UUID showId = openShow(eventId);
        EventPhoto photo = photoOnShow(photoId, showId);
        if (photo.getStatus() != PhotoStatus.APPROVED) {
            viewer(photo, jwt);
        }
        byte[] bytes = eventPhotoStorage.get(EventPhotoStorage.key(photoId, variant))
                .orElseThrow(this::photoNotFound);
        return new PhotoFile(bytes, photo.getStatus() == PhotoStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<EventPhotoResponse> adminList(PhotoStatus status) {
        return eventPhotoQueryRepository.byStatus(status, ADMIN_LIMIT).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public EventPhotoResponse adminGet(UUID photoId) {
        return response(eventPhotoQueryRepository.find(photoId).orElseThrow(this::photoNotFound));
    }

    @Transactional
    public EventPhotoResponse approve(UUID adminId, UUID photoId) {
        return review(adminId, photoId, PhotoStatus.APPROVED);
    }

    @Transactional
    public EventPhotoResponse reject(UUID adminId, UUID photoId) {
        return review(adminId, photoId, PhotoStatus.REJECTED);
    }

    @Transactional
    public void adminDelete(UUID adminId, UUID photoId) {
        requireUser(adminId);
        EventPhoto photo = eventPhotoRepository.findById(photoId).orElseThrow(this::photoNotFound);
        remove(photo, adminId);
    }

    private EventPhotoResponse review(UUID adminId, UUID photoId, PhotoStatus status) {
        requireUser(adminId);
        EventPhoto photo = eventPhotoRepository.findById(photoId).orElseThrow(this::photoNotFound);
        photo.mark(status);
        return response(eventPhotoRepository.save(photo));
    }

    private EventPhotoResponse insert(UUID id, UUID showId, UserAccount uploader) {
        lockUser(uploader.getId());
        if (openCount(uploader.getId(), showId) >= MAX_PER_EVENT) {
            throw tooMany();
        }
        EventPhoto photo = eventPhotoRepository.saveAndFlush(
                EventPhoto.pending(id, showId, uploader.getId(), clock.instant()));
        return response(photo, uploader.getDisplayName());
    }

    private void remove(EventPhoto photo, UUID resolverId) {
        adminReportRepository.resolveOpenPhotoReports(photo.getId(), resolverId, now());
        eventPhotoRepository.delete(photo);
        UUID photoId = photo.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPhotoStorage.delete(EventPhotoStorage.key(photoId, PhotoVariant.CARD));
                eventPhotoStorage.delete(EventPhotoStorage.key(photoId, PhotoVariant.FULL));
            }
        });
    }

    private long openCount(UUID userId, UUID showId) {
        return eventPhotoRepository.countByUploaderIdAndEventIdAndStatusIn(userId, showId, COUNTED);
    }

    private void lockUser(UUID userId) {
        jdbc.sql("SELECT id FROM app.app_user WHERE id = :id FOR UPDATE")
                .param("id", userId)
                .query(UUID.class)
                .optional()
                .orElseThrow(AccountNotFoundException::new);
    }

    private UUID newId() {
        return jdbc.sql("SELECT uuidv7()").query(UUID.class).single();
    }

    private UserAccount requireUser(UUID userId) {
        return userAccountRepository.findById(userId).orElseThrow(AccountNotFoundException::new);
    }

    private UUID openShow(UUID eventId) {
        return eventPhotoQueryRepository.findOpenShow(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    private EventPhoto photoOnShow(UUID photoId, UUID showId) {
        return eventPhotoRepository.findById(photoId)
                .filter(photo -> photo.getEventId().equals(showId))
                .orElseThrow(this::photoNotFound);
    }

    private void viewer(EventPhoto photo, Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid access token is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        if (!userAccountRepository.existsById(userId)) {
            throw new AccountNotFoundException();
        }
        if (!userId.equals(photo.getUploaderId()) && !isAdmin(jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view this photo if you uploaded it.");
        }
    }

    private static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }

    private EventPhotoResponse response(EventPhoto photo) {
        String displayName = userAccountRepository.findById(photo.getUploaderId())
                .map(UserAccount::getDisplayName)
                .orElse("Deleted user");
        return response(photo, displayName);
    }

    private EventPhotoResponse response(EventPhoto photo, String displayName) {
        String title = eventPhotoQueryRepository.title(photo.getEventId());
        return response(photo.getId(), photo.getEventId(), title, photo.getStatus(), photo.getCreatedAt(), displayName);
    }

    private EventPhotoResponse response(PhotoView view) {
        return response(view.id(), view.eventId(), view.eventTitle(), view.status(), view.createdAt(),
                view.displayName());
    }

    private EventPhotoResponse response(UUID id, UUID eventId, String eventTitle, PhotoStatus status,
                                        java.time.Instant createdAt, String displayName) {
        return new EventPhotoResponse(id, eventId, eventTitle, status,
                eventPhotoUrls.card(eventId, id, status),
                eventPhotoUrls.full(eventId, id, status),
                createdAt,
                new PhotoUploader(displayName));
    }

    private ResponseStatusException photoNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found.");
    }

    private static ResponseStatusException tooMany() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "You can upload at most 10 photos for this event.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public record PhotoFile(byte[] bytes, boolean approved) {
    }
}
