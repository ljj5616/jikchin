package com.jikchin.jikchin_app.application.port.in.auth;

import com.jikchin.jikchin_app.application.dto.auth.SocialLoginRequest;
import com.jikchin.jikchin_app.application.dto.auth.SocialLoginResponse;

public interface SocialLoginUseCase {
    SocialLoginResponse login(SocialLoginRequest request);
}
