package com.jikchin.jikchin_app.auth.controller;

import com.jikchin.jikchin_app.auth.dto.SocialLoginHttpResponse;
import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.auth.dto.AccessTokenRequest;
import com.jikchin.jikchin_app.auth.domain.OAuthProvider;
import com.jikchin.jikchin_app.user.dto.CompleteProfileHttpRequest;
import com.jikchin.jikchin_app.user.dto.CompleteProfileHttpResponse;
import com.jikchin.jikchin_app.auth.dto.SocialLoginCommand;
import com.jikchin.jikchin_app.auth.dto.SocialLoginResult;
import com.jikchin.jikchin_app.auth.service.SocialLoginService;
import com.jikchin.jikchin_app.auth.service.RefreshSessionService;
import com.jikchin.jikchin_app.user.dto.CompleteProfileCommand;
import com.jikchin.jikchin_app.user.service.CompleteProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
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
    private final RefreshSessionService refreshSessions;

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
        SocialLoginResult result;
        try {
            result = socialLoginService.login(cmd);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid social token");
        }
        return ResponseEntity.ok(new SocialLoginHttpResponse(
                result.needProfile(),
                result.accessToken(),
                result.refreshToken()
        ));
    }

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {}
    public record LogoutRequest(String refreshToken) {}
    public record SessionResponse(boolean needProfile) {}
    public record NicknameAvailabilityResponse(boolean available) {}

    @Operation(summary = "닉네임 사용 가능 여부 확인", description = "온보딩 액세스 토큰이 필요합니다. 최종 중복 검사는 프로필 완성 시 수행합니다.")
    @GetMapping("/nickname-availability")
    public NicknameAvailabilityResponse nicknameAvailability(
            @RequestParam @NotBlank @Size(max = 20) String nickname) {
        return new NicknameAvailabilityResponse(completeProfileService.isNicknameAvailable(nickname));
    }

    @PostMapping("/refresh")
    public RefreshSessionService.TokenPair refresh(@RequestBody @Valid RefreshRequest request) {
        return refreshSessions.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        refreshSessions.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal CurrentUser me) {
        return new SessionResponse(me.needProfile());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 프로필 완료: POST /auth/complete-profile
    // 요구: 온보딩 액세스 토큰(needProfile=true)
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

        com.jikchin.jikchin_app.user.dto.CompleteProfileResult result;
        try {
            result = completeProfileService.completeProfile(cmd);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return ResponseEntity.ok(new CompleteProfileHttpResponse(result.accessToken(), result.refreshToken()));
    }
}
