package com.jikchin.jikchin_app.application.port.out.auth;

public interface OAuthProviderPort {
    // provider + accessToken → providerId (sub) 확인
    String verifyAndGetProviderId(String provider, String accessToken);
}
