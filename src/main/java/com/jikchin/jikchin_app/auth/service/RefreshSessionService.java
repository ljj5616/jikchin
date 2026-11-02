package com.jikchin.jikchin_app.auth.service;

import com.jikchin.jikchin_app.auth.infrastructure.session.RefreshSession;
import com.jikchin.jikchin_app.auth.infrastructure.session.RefreshSessionRepository;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshSessionService {
    private final RefreshSessionRepository sessions;
    private final TokenProviderPort tokens;
    private final long ttlMillis;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository sessions, TokenProviderPort tokens,
                                 @Value("${jwt.refresh-ttl-ms:1209600000}") long ttlMillis) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.ttlMillis = ttlMillis;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new RefreshSession(hash(token), user, Instant.now().plusMillis(ttlMillis)));
        return token;
    }

    @Transactional
    public TokenPair refresh(String token) {
        RefreshSession session = sessions.findForUpdate(hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));
        User user = session.getUser();
        if (!session.getExpiresAt().isAfter(Instant.now()) || user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }
        sessions.delete(session);
        String refresh = issue(user);
        return new TokenPair(tokens.issueAccessToken(user.getId(), false), refresh);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.findForUpdate(hash(token)).ifPresent(sessions::delete);
        }
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
