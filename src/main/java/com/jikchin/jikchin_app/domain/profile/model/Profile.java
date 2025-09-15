package com.jikchin.jikchin_app.domain.profile.model;

import com.jikchin.jikchin_app.domain.team.model.Team;
import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name="idx_profile_fav_kbo", columnList="favorite_kbo_team_id"),
        @Index(name="idx_profile_fav_kleague", columnList="favorite_kleague_team_id")
})
public class Profile extends BaseEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_kbo_team_id")
    private Team favoriteKboTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_kleague_team_id")
    private Team favoriteKleagueTeam;

    public void publishDemographics(Integer birthYear, Gender gender) {
        this.birthYear = birthYear;
        this.gender = gender;
    }

    public static Profile create(User user, String nickname, String avatarUrl, String bio) {
        // 필수값 검증
        Objects.requireNonNull(user, "user");
        if (nickname == null || nickname.isBlank()) throw new IllegalArgumentException("nickname required");
        if (avatarUrl == null || avatarUrl.isBlank()) throw new IllegalArgumentException("avatarUrl required");

        Profile p = new Profile();
        p.user = user;
        p.nickname = nickname;
        p.avatarUrl = avatarUrl;
        p.bio = bio; // bio는 null 가능

        user.attachProfile(p);
        user.activate();
        return p;
    }

    // 선택값(팀) 세팅은 별도 도메인 메서드
    public void chooseFavoriteTeams(Team kbo, Team kleague) {
        this.favoriteKboTeam = kbo;
        this.favoriteKleagueTeam = kleague;
    }

}
