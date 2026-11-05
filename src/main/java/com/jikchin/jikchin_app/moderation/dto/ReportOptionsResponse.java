package com.jikchin.jikchin_app.moderation.dto;

import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;

import java.util.List;

public record ReportOptionsResponse(boolean enabled, ReportTargetType targetType, boolean descriptionRequired,
                                    int descriptionMaxLength, List<ReasonResponse> reasons) {
    public record ReasonResponse(String code, String label) {}
}
