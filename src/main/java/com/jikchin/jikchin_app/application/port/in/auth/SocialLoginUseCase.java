package com.jikchin.jikchin_app.application.port.in.auth;

public interface SocialLoginUseCase {
    SocialLoginResult login(SocialLoginCommand command);
}
