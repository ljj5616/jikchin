package com.jikchin.jikchin_app.user.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.user.dto.UserProfileResponse;
import com.jikchin.jikchin_app.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/profile")
public class UserProfileController {
    private final UserProfileService profiles;

    @Operation(summary = "사용자 프로필 조회", description = "참여자 등을 눌렀을 때 표시할 공개 프로필입니다. 본인이 차단한 상대만 닉네임에 (차단)을 표시하며 개인정보와 로그인 식별자는 반환하지 않습니다.")
    @GetMapping
    public UserProfileResponse get(@AuthenticationPrincipal CurrentUser me, @PathVariable Long userId) {
        return profiles.get(me.userId(), userId);
    }
}
