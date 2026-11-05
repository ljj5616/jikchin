package com.jikchin.jikchin_app.moderation.dto;

import com.jikchin.jikchin_app.moderation.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitReportRequest(
        @NotNull ReportReason reason,
        @Size(max = 2000) String description,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String clientReportId
) {}
