package com.jikchin.jikchin_app.domain.user.model;

import com.jikchin.jikchin_app.domain.profile.model.Profile;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public static User createSocialUser(String provider, String providerId) {
        User user = new User();
        user.provider = provider;
        user.providerId = providerId;
        user.status = UserStatus.ACTIVE;
        return user;
    }
}
