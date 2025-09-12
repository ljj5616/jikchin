package com.jikchin.jikchin_app.domain.user.model;

import com.jikchin.jikchin_app.domain.chat_thread.model.ChatThread;
import com.jikchin.jikchin_app.domain.profile.model.Profile;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "providerId"})
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
    private Profile profile;

    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY)
    private List<ChatThread> createdThreads = new ArrayList<>();

    public static User createSocialUser(String provider, String providerId) {
        User user = new User();
        user.provider = provider;
        user.providerId = providerId;
        user.status = UserStatus.REGISTERING;
        return user;
    }

    public void attachProfile(Profile profile) {
        this.profile = profile;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
