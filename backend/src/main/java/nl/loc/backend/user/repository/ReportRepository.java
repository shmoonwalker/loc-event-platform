package nl.loc.backend.user.repository;

import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.user.model.Report;
import nl.loc.backend.user.model.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByTargetTypeAndTargetIdAndReporterIdAndResolvedAtIsNull(
            ReportTargetType targetType, UUID targetId, UUID reporterId);
}
