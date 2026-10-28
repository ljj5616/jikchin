package com.jikchin.jikchin_app.auth.port;

public interface TokenProviderPort {
    String issueAccessToken(Long userId, boolean needProfile);

    String issueRefreshToken(Long userId);

    ParsedToken parse(String token);

    record ParsedToken(Long userId, boolean needProfile, String typ) {}
}
