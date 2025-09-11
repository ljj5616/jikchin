package com.jikchin.jikchin_app.domain.profile.model;

import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20, unique = true)
    private String nickname;

    @Column(nullable = false, length = 200)
    private String avatarUrl;

    @Column(length = 160)
    private String bio;

    @Column
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    public void publishDemographics(Integer birthYear, Gender gender) {
        this.birthYear = birthYear;
        this.gender = gender;
    }

    public static Profile create(User user, String nickname, String avatarUrl, String bio) {
        Profile profile = new Profile();
        profile.user = user;
        profile.nickname = nickname;
        profile.avatarUrl = avatarUrl;
        profile.bio = bio;

        user.attachProfile(profile);
        user.activate();

        return profile;
    }
}
