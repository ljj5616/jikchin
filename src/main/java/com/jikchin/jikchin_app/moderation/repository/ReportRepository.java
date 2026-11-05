package com.jikchin.jikchin_app.moderation.repository;

import com.jikchin.jikchin_app.moderation.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    Optional<Report> findByReporter_IdAndClientReportId(Long reporterId, String clientReportId);
}
