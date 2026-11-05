package com.jikchin.jikchin_app.chat.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.chat.dto.ChatHistoryResponse;
import com.jikchin.jikchin_app.chat.dto.ChatMessageResponse;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class RoomChatController {
    private final RoomChatService chat;

    @GetMapping
    public ChatHistoryResponse history(@AuthenticationPrincipal CurrentUser user, @PathVariable Long roomId,
                                       @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "50") int size) {
        return chat.history(user.userId(), roomId, beforeId, size);
    }

    @PostMapping
    public ChatMessageResponse send(@AuthenticationPrincipal CurrentUser user, @PathVariable Long roomId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return chat.send(user.userId(), roomId, request);
    }
}
