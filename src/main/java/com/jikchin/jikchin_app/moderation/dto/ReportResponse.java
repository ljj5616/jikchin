package com.jikchin.jikchin_app.moderation.dto;

import com.jikchin.jikchin_app.moderation.domain.Report;
import com.jikchin.jikchin_app.moderation.domain.ReportStatus;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;

import java.time.LocalDateTime;

public record ReportResponse(Long id, ReportTargetType targetType, Long targetId, String reason,
                             ReportStatus status, LocalDateTime createdAt) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getStatus(), report.getCreatedAt());
    }
}
