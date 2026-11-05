package com.jikchin.jikchin_app.chat.dto;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages, boolean hasMore, Long nextBeforeId) {}
