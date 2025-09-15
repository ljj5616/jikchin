package com.jikchin.jikchin_app.application.port.out.auth;

public interface TokenProviderPort {
    String issueAccessToken(Long userId, boolean needProfile);

    String issueRefreshToken(Long userId);
}
