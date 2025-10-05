package com.jikchin.jikchin_app.adapter.in.web.auth.converter;

import com.jikchin.jikchin_app.application.port.in.auth.OAuthProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OAuthProviderConverter implements Converter<String, OAuthProvider> {
    @Override
    public OAuthProvider convert(String source) {
        if (source == null) return null;
        return switch (source.trim().toLowerCase(Locale.ROOT)) {
            case "google" -> OAuthProvider.GOOGLE;
            case "naver"  -> OAuthProvider.NAVER;
            case "kakao"  -> OAuthProvider.KAKAO;
            default -> throw new IllegalArgumentException("Unknown provider: " + source);
        };
    }
}