package com.jikchin.jikchin_app.moderation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.moderation.config.ReportProperties;
import com.jikchin.jikchin_app.moderation.domain.Report;
import com.jikchin.jikchin_app.moderation.domain.ReportReason;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;
import com.jikchin.jikchin_app.moderation.dto.ReportOptionsResponse;
import com.jikchin.jikchin_app.moderation.dto.ReportOptionsResponse.ReasonResponse;
import com.jikchin.jikchin_app.moderation.dto.ReportResponse;
import com.jikchin.jikchin_app.moderation.dto.SubmitReportRequest;
import com.jikchin.jikchin_app.moderation.repository.ReportRepository;
import com.jikchin.jikchin_app.room.domain.Room;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reports;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final ReportProperties properties;
    private final ObjectMapper json;
    private final Validator validator;

    public ReportOptionsResponse options(ReportTargetType targetType) {
        var reasons = Arrays.stream(ReportReason.values()).filter(reason -> reason.supports(targetType))
                .map(reason -> new ReasonResponse(reason.name(), reason.getLabel())).toList();
        return new ReportOptionsResponse(properties.isEnabled(), targetType, targetType == ReportTargetType.USER, 2000, reasons);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Submission submit(Long reporterId, ReportTargetType targetType, Long targetId, SubmitReportRequest request) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "신고 접수가 일시 중단되어 있습니다.");
        }
        if (targetId == null || targetId < 1 || targetType == null || request == null
                || !validator.validate(request).isEmpty() || !request.reason().supports(targetType)) {
            throw invalid("신고 대상과 해당 대상에 맞는 사유를 선택해 주세요.");
        }
        String description = request.description() == null ? null : request.description().strip();
        if (description != null && description.isBlank()) description = null;
        if (targetType == ReportTargetType.USER && description == null) {
            throw invalid("유저 신고 내용을 입력해 주세요.");
        }
        if (targetType == ReportTargetType.USER && reporterId.equals(targetId)) {
            throw invalid("본인은 신고할 수 없습니다.");
        }
        String clientId = request.clientReportId() == null ? null : request.clientReportId().toLowerCase(Locale.ROOT);
        reporter(reporterId, false);
        var retry = previous(reporterId, clientId);
        if (retry.isPresent()) return retry(retry.get(), targetType, targetId, request.reason(), description);

        // Match room join/leave lock order: room first, then user. No target FK blocks future deletion.
        Room room = targetType == ReportTargetType.ROOM ? rooms.findByIdForUpdate(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다.")) : null;
        User reporter = reporter(reporterId, true);
        // Concurrent submissions with the same retry key serialize on the reporter row.
        retry = previous(reporterId, clientId);
        if (retry.isPresent()) return retry(retry.get(), targetType, targetId, request.reason(), description);

        String snapshot;
        if (room != null) {
            if (reporterId.equals(room.getChatThread().getCreatedBy().getId())) {
                throw invalid("본인이 만든 방은 신고할 수 없습니다.");
            }
            snapshot = snapshot(new RoomSnapshot(room.getId(), room.getTitle(), room.getGameDate(), room.getStadium(),
                    room.getHomeTeam().getId(), room.getHomeTeam().getName(), room.getAwayTeam().getId(),
                    room.getAwayTeam().getName(), room.getChatThread().getCreatedBy().getId(), room.getHost().getId(),
                    room.getChatThread().getCapacity()));
        } else {
            User target = users.findById(targetId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
            var profile = target.getProfile();
            snapshot = snapshot(new UserSnapshot(targetId, profile == null ? null : profile.getNickname(),
                    profile == null ? null : profile.getAvatarKey(), profile == null ? null : profile.getBio()));
        }
        var report = reports.saveAndFlush(Report.create(reporter, targetType, targetId, request.reason(), description, snapshot, clientId));
        return new Submission(ReportResponse.from(report), true);
    }

    private Optional<Report> previous(Long reporterId, String clientId) {
        return clientId == null ? Optional.empty() : reports.findByReporter_IdAndClientReportId(reporterId, clientId);
    }

    private User reporter(Long userId, boolean lock) {
        User user = (lock ? users.findByIdForUpdate(userId) : users.findById(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로필 등록을 완료해 주세요.");
        }
        return user;
    }

    private Submission retry(Report report, ReportTargetType type, Long targetId, ReportReason reason, String description) {
        if (report.getTargetType() != type || !report.getTargetId().equals(targetId)
                || !report.getReason().equals(reason.name()) || !Objects.equals(report.getDescription(), description)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "새 신고에는 새로운 clientReportId를 사용해 주세요.");
        }
        return new Submission(ReportResponse.from(report), false);
    }

    private String snapshot(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("신고 대상 정보를 저장할 수 없습니다.", e);
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record Submission(ReportResponse report, boolean created) {}
    private record UserSnapshot(Long userId, String nickname, String avatarKey, String bio) {}
    private record RoomSnapshot(Long roomId, String title, LocalDate gameDate, String stadium,
                                Integer homeTeamId, String homeTeam, Integer awayTeamId, String awayTeam,
                                Long creatorId, Long hostId, int capacity) {}
}
