package com.jikchin.jikchin_app.auth.controller;

import com.jikchin.jikchin_app.auth.dto.SocialLoginHttpResponse;
import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.auth.dto.AccessTokenRequest;
import com.jikchin.jikchin_app.auth.domain.OAuthProvider;
import com.jikchin.jikchin_app.user.dto.CompleteProfileHttpRequest;
import com.jikchin.jikchin_app.user.dto.CompleteProfileHttpResponse;
import com.jikchin.jikchin_app.auth.dto.SocialLoginCommand;
import com.jikchin.jikchin_app.auth.service.SocialLoginService;
import com.jikchin.jikchin_app.user.dto.CompleteProfileCommand;
import com.jikchin.jikchin_app.user.service.CompleteProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final SocialLoginService socialLoginService;
    private final CompleteProfileService completeProfileService;

    /**
     * 소셜 로그인 엔드포인트
     * body: { "provider": "google|naver|kakao", "accessToken": "..." }
     * 반환: needProfile=true면 온보딩용 액세스 토큰(프로필 완성 필요)
     */

    @PostMapping("/{provider}/login")
    public ResponseEntity<SocialLoginHttpResponse> socialLogin(
            @PathVariable OAuthProvider provider,
            @RequestBody @Valid AccessTokenRequest body
    ) {
        var cmd = new SocialLoginCommand(provider.name().toLowerCase(), body.getAccessToken());
        var result = socialLoginService.login(cmd);
        return ResponseEntity.ok(new SocialLoginHttpResponse(
                result.needProfile(),
                result.accessToken(),
                result.refreshToken()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 프로필 완료: POST /auth/complete-profile
    // 요구: 온보딩 액세스 토큰(needProfile=true)
    // JWT 필터가 request.setAttribute("userId", ...), ("needProfile", ...) 셋팅했다고 가정
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/complete-profile")
    public ResponseEntity<CompleteProfileHttpResponse> completeProfile(
            @AuthenticationPrincipal CurrentUser me,
            @RequestBody @Valid CompleteProfileHttpRequest request
    ) {
        // 온보딩 토큰만 허용
        if (!me.needProfile()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new CompleteProfileHttpResponse(null, null));
        }

        var cmd = new CompleteProfileCommand(
                me.userId(),
                request.nickname(),
                request.avatarKey(),
                request.bio(),
                request.favoriteKboTeamId(),
                request.favoriteKleagueTeamId()
        );

        var result = completeProfileService.completeProfile(cmd);

        return ResponseEntity.ok(new CompleteProfileHttpResponse(result.accessToken(), result.refreshToken()));
    }
}
