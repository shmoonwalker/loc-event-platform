package nl.loc.backend.photo.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.photo.model.EventPhoto;
import nl.loc.backend.photo.model.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, UUID> {

    long countByUploaderIdAndEventIdAndStatusIn(UUID uploaderId, UUID eventId, Collection<PhotoStatus> statuses);

    @Query("SELECT p.id FROM EventPhoto p WHERE p.uploaderId = :uploaderId")
    List<UUID> findIdsByUploaderId(@Param("uploaderId") UUID uploaderId);
}
