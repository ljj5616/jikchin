package com.jikchin.jikchin_app.moderation.dto;

import java.time.LocalDateTime;

public record BlockedUserResponse(Long userId, String nickname, String avatarUrl, LocalDateTime blockedAt) {}
