package com.jikchin.jikchin_app.application.usecase.profile;

import com.jikchin.jikchin_app.application.port.in.profile.CompleteProfileCommand;
import com.jikchin.jikchin_app.application.port.in.profile.CompleteProfileResult;
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
    public CompleteProfileResult completeProfile(CompleteProfileCommand command) {
        User user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (profileRepositoryPort.existsByNickname(command.nickname())) {
            throw new IllegalArgumentException("nickname already taken");
        }

        Team kboTeam = null;
        if (command.favoriteKboTeamId() != null) {
            kboTeam = kboTeamQueryPort.findById(command.favoriteKboTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("KBO team not found"));
        }

        Team kleagueTeam = null;
        if (command.favoriteKleagueTeamId() != null) {
            kleagueTeam = kLeagueTeamQueryPort.findById(command.favoriteKleagueTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("K League team not found"));
        }

        String avatarKey = pickAvatarKey(command.userId(), command.avatarKey());

        Profile profile = Profile.create(
                user,
                command.nickname(),
                avatarKey,
                command.bio()
        );
        if (kboTeam != null || kleagueTeam != null) {
            profile.chooseFavoriteTeams(kboTeam, kleagueTeam);
        }
        profileRepositoryPort.save(profile);

        String access  = tokenProviderPort.issueAccessToken(user.getId(), false);
        String refresh = tokenProviderPort.issueRefreshToken(user.getId());

        return new CompleteProfileResult(access, refresh);
    }
}