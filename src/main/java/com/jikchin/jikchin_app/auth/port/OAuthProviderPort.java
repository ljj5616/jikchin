package com.jikchin.jikchin_app.auth.port;

public interface OAuthProviderPort {
    // provider + accessToken → providerId (sub) 확인
    String verifyAndGetProviderId(String provider, String accessToken);
}
