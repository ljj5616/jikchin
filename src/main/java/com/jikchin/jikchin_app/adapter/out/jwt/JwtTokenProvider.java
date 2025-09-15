package com.jikchin.jikchin_app.adapter.out.jwt;

import com.jikchin.jikchin_app.application.port.out.auth.TokenProviderPort;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider implements TokenProviderPort {

    private final SecretKey key;
    private final long accessTtlMillis;
    private final long onboardingAccessTtlMillis;
    private final long refreshTtlMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-ms:900000}") long accessTtlMillis,              // 15분
            @Value("${jwt.onboarding-access-ttl-ms:1800000}") long onboardingTtl,   // 30분
            @Value("${jwt.refresh-ttl-ms:1209600000}") long refreshTtlMillis        // 14일
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessTtlMillis;
        this.onboardingAccessTtlMillis = onboardingTtl;
        this.refreshTtlMillis = refreshTtlMillis;
    }

    @Override
    public String issueAccessToken(Long userId, boolean needProfile) {
        long ttl = needProfile ? onboardingAccessTtlMillis : accessTtlMillis;
        return buildJwt(userId, ttl, Map.of("needProfile", needProfile, "typ", "access"));
    }

    @Override
    public String issueRefreshToken(Long userId) {
        return buildJwt(userId, refreshTtlMillis, Map.of("typ", "refresh"));
    }

    private String buildJwt(Long userId, long ttlMillis, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key)
                .compact();
    }
}
