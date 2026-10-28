package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.user.dto.CompleteProfileCommand;
import com.jikchin.jikchin_app.user.dto.CompleteProfileResult;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.game.repository.TeamRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.game.domain.Team;
import com.jikchin.jikchin_app.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CompleteProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TeamRepository teamRepository;
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

    @Transactional
    public CompleteProfileResult completeProfile(CompleteProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (profileRepository.existsByNickname(command.nickname())) {
            throw new IllegalArgumentException("nickname already taken");
        }

        Team kboTeam = null;
        if (command.favoriteKboTeamId() != null) {
            kboTeam = teamRepository.findById(command.favoriteKboTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("KBO team not found"));
        }

        Team kleagueTeam = null;
        if (command.favoriteKleagueTeamId() != null) {
            kleagueTeam = teamRepository.findById(command.favoriteKleagueTeamId())
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
        profileRepository.save(profile);

        String access  = tokenProviderPort.issueAccessToken(user.getId(), false);
        String refresh = tokenProviderPort.issueRefreshToken(user.getId());

        return new CompleteProfileResult(access, refresh);
    }
}
