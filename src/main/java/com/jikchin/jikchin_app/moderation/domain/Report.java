package com.jikchin.jikchin_app.moderation.domain;

import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_report_client", columnNames = {"reporter_id", "client_report_id"}))
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Keep legacy free-text reasons readable; new submissions use ReportReason codes.
    @Column(nullable = false, length = 40)
    private String reason;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    // No FK to the target: reports and their snapshots survive room deletion.
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Lob
    @Column(name = "target_snapshot", nullable = false, columnDefinition = "text")
    private String targetSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "client_report_id", length = 36)
    private String clientReportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    public static Report create(User reporter, ReportTargetType targetType, Long targetId, ReportReason reason,
                                String description, String targetSnapshot, String clientReportId) {
        Report report = new Report();
        report.reporter = reporter;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason.name();
        report.description = description;
        report.targetSnapshot = targetSnapshot;
        report.status = ReportStatus.PENDING;
        report.clientReportId = clientReportId;
        return report;
    }
}
