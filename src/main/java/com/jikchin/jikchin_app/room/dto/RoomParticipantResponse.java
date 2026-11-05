package com.jikchin.jikchin_app.room.dto;

import java.time.LocalDateTime;

public record RoomParticipantResponse(Long userId, String nickname, String avatarUrl,
                                      boolean host, LocalDateTime joinedAt) {}
