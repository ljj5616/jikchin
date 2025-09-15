package com.jikchin.jikchin_app.adapter.in.web.auth.controller;

import com.jikchin.jikchin_app.application.dto.auth.AccessTokenRequest;
import com.jikchin.jikchin_app.application.dto.auth.OAuthProvider;
import com.jikchin.jikchin_app.application.dto.auth.SocialLoginRequest;
import com.jikchin.jikchin_app.application.dto.auth.SocialLoginResponse;
import com.jikchin.jikchin_app.application.dto.profile.CompleteProfileRequest;
import com.jikchin.jikchin_app.application.dto.profile.CompleteProfileResponse;
import com.jikchin.jikchin_app.application.port.in.auth.SocialLoginUseCase;
import com.jikchin.jikchin_app.application.port.in.profile.CompleteProfileUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final CompleteProfileUseCase completeProfileUseCase;

    /**
     * 소셜 로그인 엔드포인트
     * body: { "provider": "google|naver|kakao", "accessToken": "..." }
     * 반환: needProfile=true면 온보딩용 액세스 토큰(프로필 완성 필요)
     */

    @PostMapping("/{provider}/login")
    public ResponseEntity<SocialLoginResponse> socialLogin(
            @PathVariable OAuthProvider provider,
            @RequestBody @Valid AccessTokenRequest body
    ) {
        SocialLoginRequest request =
                new SocialLoginRequest(provider.name().toLowerCase(), body.getAccessToken());
        return ResponseEntity.ok(socialLoginUseCase.login(request));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 프로필 완료: POST /auth/complete-profile
    // 요구: 온보딩 액세스 토큰(needProfile=true)
    // JWT 필터가 request.setAttribute("userId", ...), ("needProfile", ...) 셋팅했다고 가정
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/complete-profile")
    public ResponseEntity<CompleteProfileResponse> completeProfile(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "needProfile", required = false) Boolean needProfile,
            @RequestBody @Valid CompleteProfileRequest request
            ) {
        if (needProfile == null || !needProfile) {
            // 이미 프로필이 완료된 토큰으로 접근한 경우 방어
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CompleteProfileResponse.builder()
                            .accessToken(null)
                            .refreshToken(null)
                            .build());
        }
        return ResponseEntity.ok(completeProfileUseCase.completeProfile(userId, request));
    }
}
