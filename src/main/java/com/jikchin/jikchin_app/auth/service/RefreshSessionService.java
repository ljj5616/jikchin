package com.jikchin.jikchin_app.auth.service;

import com.jikchin.jikchin_app.auth.infrastructure.session.RefreshSession;
import com.jikchin.jikchin_app.auth.infrastructure.session.RefreshSessionRepository;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
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
    private final UserRepository users;
    private final long ttlMillis;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository sessions, TokenProviderPort tokens, UserRepository users,
                                 @Value("${jwt.refresh-ttl-ms:1209600000}") long ttlMillis) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.users = users;
        this.ttlMillis = ttlMillis;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String issue(User user) {
        lockActiveUser(user.getId());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new RefreshSession(hash(token), user, Instant.now().plusMillis(ttlMillis)));
        return token;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TokenPair refresh(String token) {
        String tokenHash = hash(token);
        var candidate = sessions.findById(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));
        // Use user -> session order for both rotation and withdrawal. No token can be
        // inserted after withdrawal has cleared all sessions under the user lock.
        User user = lockActiveUser(candidate.getUser().getId());
        RefreshSession session = sessions.findForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }
        sessions.delete(session);
        String refresh = issue(user);
        return new TokenPair(tokens.issueAccessToken(user.getId(), false), refresh);
    }

    private User lockActiveUser(Long userId) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account unavailable"));
        if (users.findStatusById(userId).orElseThrow() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account unavailable");
        }
        return user;
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
