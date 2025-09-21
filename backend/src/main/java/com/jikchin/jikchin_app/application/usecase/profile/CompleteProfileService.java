package com.jikchin.jikchin_app.application.usecase.profile;

import com.jikchin.jikchin_app.application.dto.profile.CompleteProfileRequest;
import com.jikchin.jikchin_app.application.dto.profile.CompleteProfileResponse;
import com.jikchin.jikchin_app.application.port.in.profile.CompleteProfileUseCase;
import com.jikchin.jikchin_app.application.port.out.auth.TokenProviderPort;
import com.jikchin.jikchin_app.application.port.out.profile.ProfileRepositoryPort;
import com.jikchin.jikchin_app.application.port.out.team.KLeagueTeamQueryPort;
import com.jikchin.jikchin_app.application.port.out.team.KboTeamQueryPort;
import com.jikchin.jikchin_app.application.port.out.user.UserRepositoryPort;
import com.jikchin.jikchin_app.domain.profile.model.Profile;
import com.jikchin.jikchin_app.domain.team.model.Team;
import com.jikchin.jikchin_app.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CompleteProfileService implements CompleteProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final ProfileRepositoryPort profileRepositoryPort;
    private final KboTeamQueryPort kboTeamQueryPort;
    private final KLeagueTeamQueryPort kLeagueTeamQueryPort;
    private final TokenProviderPort tokenProviderPort;

    @Value("${app.assets.base-url}")       String baseUrl;
    @Value("${app.assets.default-avatar-key}") String defaultKey;

    private String toKey(String maybeUrlOrKey) {
        if (!StringUtils.hasText(maybeUrlOrKey)) return null;
        // 절대 URL로 왔으면 base-url 제거해 key로 변환
        if (maybeUrlOrKey.startsWith(baseUrl + "/")) {
            return maybeUrlOrKey.substring((baseUrl + "/").length());
        }
        // s3://bucket/key 형식이면 key만 추출
        if (maybeUrlOrKey.startsWith("s3://")) {
            int firstSlash = maybeUrlOrKey.indexOf('/', "s3://".length());
            return firstSlash > 0 ? maybeUrlOrKey.substring(firstSlash + 1) : null;
        }
        // 이미 key일 수도 있음
        return maybeUrlOrKey;
    }

    private String pickAvatarKey(Long userId, String reqAvatar) {
        String key = toKey(reqAvatar);
        if (!StringUtils.hasText(key)) return defaultKey;
        // 보안: 내 경로만 허용
        if (!key.startsWith("avatars/users/" + userId + "/") &&
                !key.startsWith("avatars/defaults/")) {
            throw new IllegalArgumentException("invalid avatar key");
        }
        return key;
    }

    @Override
    @Transactional
    public CompleteProfileResponse completeProfile(Long userId, CompleteProfileRequest request) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (profileRepositoryPort.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("nickname already taken");
        }

        Team kboTeam = null;
        if (request.getFavoriteKboTeamId() != null) {
            kboTeam = kboTeamQueryPort.findById(request.getFavoriteKboTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("KBO team not found"));
        }

        Team kleagueTeam = null;
        if (request.getFavoriteKleagueTeamId() != null) {
            kleagueTeam = kLeagueTeamQueryPort.findById(request.getFavoriteKleagueTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("K League team not found"));
        }

        // URL이 아닌 'key'를 저장
        String avatarKey = pickAvatarKey(userId, request.getAvatarKey()); // (임시: 필드명이 avatarUrl이면 그대로 받아 key로 변환)

        Profile profile = Profile.create(
                user,
                request.getNickname(),
                avatarKey,     // "Url" 대신 "Key"
                request.getBio()
        );
        if (kboTeam != null || kleagueTeam != null) {
            profile.chooseFavoriteTeams(kboTeam, kleagueTeam);
        }

        profileRepositoryPort.save(profile);

        String access = tokenProviderPort.issueAccessToken(user.getId(), false);
        String refresh = tokenProviderPort.issueRefreshToken(user.getId());

        return CompleteProfileResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .build();
    }
}
