package nl.loc.backend.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nl.loc.backend.admin.dto.ReportGroupResponse;
import nl.loc.backend.admin.repository.AdminReportRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import nl.loc.backend.user.service.AccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminReportService {

    private final AdminReportRepository adminReportRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public AdminReportService(AdminReportRepository adminReportRepository,
                              UserAccountRepository userAccountRepository, Clock clock) {
        this.adminReportRepository = adminReportRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReportGroupResponse> openReports() {
        return adminReportRepository.listOpen();
    }

    @Transactional
    public void dismiss(UUID adminId, UUID reportId) {
        if (!userAccountRepository.existsById(adminId)) {
            throw new AccountNotFoundException();
        }
        if (adminReportRepository.dismiss(reportId, adminId, now()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
