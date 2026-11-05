package com.jikchin.jikchin_app.moderation.domain;

import lombok.Getter;

import java.util.Set;

import static com.jikchin.jikchin_app.moderation.domain.ReportTargetType.*;

@Getter
public enum ReportReason {
    ABUSE("욕설·비방·혐오 표현", USER, ROOM),
    HARASSMENT("괴롭힘·위협·스토킹", USER),
    SEXUAL_CONTENT("음란·성적 불쾌감 유발", USER, ROOM),
    SPAM("광고·도배", USER, ROOM),
    FRAUD("사기·금전 요구", USER, ROOM),
    PRIVACY_VIOLATION("개인정보 노출·침해", USER, ROOM),
    IMPERSONATION("타인 사칭", USER),
    INAPPROPRIATE_PROFILE("부적절한 닉네임·프로필 사진", USER),
    MISLEADING_ROOM_INFO("허위 경기·모집 정보", ROOM),
    ILLEGAL_ACTIVITY("불법 거래·활동 유도", ROOM),
    OTHER("기타", USER, ROOM);

    private final String label;
    private final Set<ReportTargetType> targetTypes;

    ReportReason(String label, ReportTargetType... targetTypes) {
        this.label = label;
        this.targetTypes = Set.of(targetTypes);
    }

    public boolean supports(ReportTargetType targetType) {
        return targetTypes.contains(targetType);
    }
}
