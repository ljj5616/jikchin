package com.jikchin.jikchin_app.auth.infrastructure.session;

import com.jikchin.jikchin_app.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "auth_refresh_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RefreshSession(String tokenHash, User user, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }
}
