package com.jikchin.jikchin_app.moderation.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.moderation.dto.BlockedUserResponse;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/blocks")
public class BlockController {
    private final BlockService blocks;

    @Operation(summary = "차단한 사용자 목록", description = "본인이 차단한 사용자를 최근 차단 순서로 반환합니다. nickname에는 (차단)이 붙습니다.")
    @GetMapping
    public List<BlockedUserResponse> list(@AuthenticationPrincipal CurrentUser me) {
        return blocks.list(me.userId());
    }

    @Operation(summary = "사용자 차단", description = "서로가 최초 생성한 방의 새 입장을 제한합니다. 이미 차단한 사용자를 다시 요청해도 성공합니다.")
    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId) {
        blocks.block(me.userId(), userId);
    }

    @Operation(summary = "사용자 차단 해제", description = "본인의 차단만 해제합니다. 상대의 차단이 남아 있으면 방 입장 제한은 유지됩니다.")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId) {
        blocks.unblock(me.userId(), userId);
    }
}
