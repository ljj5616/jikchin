package com.jikchin.jikchin_app.moderation.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;
import com.jikchin.jikchin_app.moderation.dto.ReportOptionsResponse;
import com.jikchin.jikchin_app.moderation.dto.ReportResponse;
import com.jikchin.jikchin_app.moderation.dto.SubmitReportRequest;
import com.jikchin.jikchin_app.moderation.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;

    @Operation(summary = "신고 사유와 활성화 상태", description = "USER 또는 ROOM에 맞는 사유, 상세 내용 필수 여부와 최대 길이를 반환합니다. 비활성화 상태에서도 조회할 수 있습니다.")
    @GetMapping("/reasons")
    public ReportOptionsResponse reasons(@RequestParam ReportTargetType targetType) {
        return reports.options(targetType);
    }

    @Operation(summary = "유저 신고", description = "사유와 1~2000자의 상세 내용을 입력합니다. 관리자 검토 대기 상태로 저장하며 선택 사항인 clientReportId(UUID)로 재전송 중복을 방지할 수 있습니다.")
    @PostMapping("/users/{userId}")
    public ResponseEntity<ReportResponse> user(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId,
                                              @RequestBody @Valid SubmitReportRequest request) {
        return response(reports.submit(me.userId(), ReportTargetType.USER, userId, request));
    }

    @Operation(summary = "방 신고", description = "참여 전 목록과 참여 중 메뉴에서 같은 API를 사용합니다. 상세 내용은 선택 사항이며 최대 2000자입니다. 관리자 페이지 없이도 DB에 접수됩니다.")
    @PostMapping("/rooms/{roomId}")
    public ResponseEntity<ReportResponse> room(@AuthenticationPrincipal CurrentUser me, @PathVariable Long roomId,
                                              @RequestBody @Valid SubmitReportRequest request) {
        return response(reports.submit(me.userId(), ReportTargetType.ROOM, roomId, request));
    }

    private ResponseEntity<ReportResponse> response(ReportService.Submission submission) {
        return ResponseEntity.status(submission.created() ? HttpStatus.CREATED : HttpStatus.OK).body(submission.report());
    }
}
