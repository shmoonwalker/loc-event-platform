package nl.loc.backend.user.service;

import java.time.Clock;
import java.util.UUID;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.photo.model.EventPhoto;
import nl.loc.backend.photo.model.PhotoStatus;
import nl.loc.backend.photo.repository.EventPhotoQueryRepository;
import nl.loc.backend.photo.repository.EventPhotoRepository;
import nl.loc.backend.user.dto.response.ReportResponse;
import nl.loc.backend.user.model.Comment;
import nl.loc.backend.user.model.Report;
import nl.loc.backend.user.model.ReportReason;
import nl.loc.backend.user.model.ReportTargetType;
import nl.loc.backend.user.repository.CommentRepository;
import nl.loc.backend.user.repository.ReportQueryRepository;
import nl.loc.backend.user.repository.ReportRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportQueryRepository reportQueryRepository;
    private final CommentRepository commentRepository;
    private final EventPhotoRepository eventPhotoRepository;
    private final EventPhotoQueryRepository eventPhotoQueryRepository;
    private final UserAccountRepository userAccountRepository;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ReportService(ReportRepository reportRepository, ReportQueryRepository reportQueryRepository,
                         CommentRepository commentRepository, EventPhotoRepository eventPhotoRepository,
                         EventPhotoQueryRepository eventPhotoQueryRepository,
                         UserAccountRepository userAccountRepository, RateLimiter rateLimiter, Clock clock) {
        this.reportRepository = reportRepository;
        this.reportQueryRepository = reportQueryRepository;
        this.commentRepository = commentRepository;
        this.eventPhotoRepository = eventPhotoRepository;
        this.eventPhotoQueryRepository = eventPhotoQueryRepository;
        this.userAccountRepository = userAccountRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public ReportResponse reportEvent(UUID reporterId, UUID eventId, ReportReason reason, String details) {
        reporter(reporterId);
        UUID showId = reportQueryRepository.findShow(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
        return ReportResponse.of(openOrCreate(ReportTargetType.EVENT, showId, reporterId, reason, details));
    }

    public ReportResponse reportComment(UUID reporterId, UUID commentId, ReportReason reason, String details) {
        reporter(reporterId);
        Comment comment = commentRepository.findById(commentId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        return ReportResponse.of(openOrCreate(ReportTargetType.COMMENT, comment.getId(), reporterId, reason, details));
    }

    public ReportResponse reportPhoto(UUID reporterId, UUID eventId, UUID photoId, ReportReason reason, String details) {
        reporter(reporterId);
        UUID showId = eventPhotoQueryRepository.findOpenShow(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));
        EventPhoto photo = eventPhotoRepository.findById(photoId)
                .filter(found -> found.getEventId().equals(showId) && found.getStatus() == PhotoStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));
        return ReportResponse.of(openOrCreate(ReportTargetType.PHOTO, photo.getId(), reporterId, reason, details));
    }

    private void reporter(UUID reporterId) {
        // the token stays valid until it expires, even if the account was deleted in the meantime
        if (userAccountRepository.findById(reporterId).isEmpty()) {
            throw new AccountNotFoundException();
        }
        rateLimiter.consume(RateLimitPolicy.REPORT, reporterId.toString());
    }

    /** The open report already stored for this member, or a new one. A repeat does not insert a second row. */
    private Report openOrCreate(ReportTargetType targetType, UUID targetId, UUID reporterId, ReportReason reason,
                                String details) {
        return reportRepository.findByTargetTypeAndTargetIdAndReporterIdAndResolvedAtIsNull(
                        targetType, targetId, reporterId)
                .orElseGet(() -> insert(targetType, targetId, reporterId, reason, blankToNull(details)));
    }

    private Report insert(ReportTargetType targetType, UUID targetId, UUID reporterId, ReportReason reason,
                          String details) {
        try {
            return reportRepository.saveAndFlush(
                    Report.open(targetType, targetId, reporterId, reason, details, clock.instant()));
        } catch (DataIntegrityViolationException ex) {
            return reportRepository.findByTargetTypeAndTargetIdAndReporterIdAndResolvedAtIsNull(
                            targetType, targetId, reporterId)
                    .orElseThrow(() -> ex);
        }
    }

    private static String blankToNull(String details) {
        if (details == null) {
            return null;
        }
        String trimmed = details.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
