package com.jikchin.jikchin_app.application.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SocialLoginResponse {
    private boolean needProfile; // true면 온보딩 단계
    private String accessToken; // 온보딩 or 최종 액세스 토큰
    private String refreshToken; // 최종 로그인 시에만 채움
}
