package com.jikchin.jikchin_app.adapter.in.web.auth.controller;

import com.jikchin.jikchin_app.application.dto.auth.AccessTokenRequest;
import com.jikchin.jikchin_app.application.dto.auth.OAuthProvider;
import com.jikchin.jikchin_app.application.dto.auth.SocialLoginRequest;
import com.jikchin.jikchin_app.application.dto.auth.SocialLoginResponse;
import com.jikchin.jikchin_app.application.port.in.auth.SocialLoginUseCase;
import com.jikchin.jikchin_app.application.port.in.profile.CompleteProfileUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;

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
}
