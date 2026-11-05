package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.game.domain.LeagueCode;
import com.jikchin.jikchin_app.game.domain.Team;
import com.jikchin.jikchin_app.game.repository.TeamRepository;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.dto.MyProfileResponse;
import com.jikchin.jikchin_app.user.dto.UpdateMyProfileRequest;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyProfileService {
    private final ProfileRepository profiles;
    private final TeamRepository teams;

    @Value("${app.assets.base-url}")
    private String assetsBaseUrl;
    @Value("${app.assets.default-avatar-key}")
    private String defaultAvatarKey;

    public MyProfileResponse get(Long userId) {
        return response(findProfile(userId));
    }

    public boolean isNicknameAvailable(Long userId, String nickname) {
        return !profiles.existsByNicknameAndUserIdNot(normalizeNickname(nickname), userId);
    }

    @Transactional
    public MyProfileResponse update(Long userId, UpdateMyProfileRequest request) {
        Profile profile = findProfile(userId);
        String nickname = normalizeNickname(request.nickname());
        if (profiles.existsByNicknameAndUserIdNot(nickname, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
        String avatarKey = avatarKey(userId, request.avatarKey());
        Team kbo = favoriteTeam(request.favoriteKboTeamId(), LeagueCode.KBO);
        Team kleague = favoriteTeam(request.favoriteKleagueTeamId(), LeagueCode.KLEAGUE);
        String bio = request.bio() == null ? null : request.bio().strip();
        if (bio != null && bio.isEmpty()) bio = null;
        try {
            profile.updateProfile(nickname, avatarKey, bio, kbo, kleague);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        try {
            profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            // 사전 확인 이후 다른 사용자가 같은 닉네임을 저장한 경우에도 고유 제약 조건을 적용합니다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.", e);
        }
        return response(profile);
    }

    private Profile findProfile(Long userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."));
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.strip().isEmpty() || nickname.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임은 1~20자로 입력해주세요.");
        }
        return nickname.strip();
    }

    private String avatarKey(Long userId, String requestedKey) {
        if (requestedKey == null) return defaultAvatarKey;
        boolean owned = requestedKey.startsWith("avatars/users/" + userId + "/");
        boolean defaultAvatar = requestedKey.equals(defaultAvatarKey) || requestedKey.startsWith("avatars/defaults/");
        boolean invalidPath = requestedKey.isBlank() || requestedKey.length() > 200 || requestedKey.endsWith("/")
                || requestedKey.chars().anyMatch(c -> Character.isISOControl(c) || "\\%?#".indexOf(c) >= 0)
                || Arrays.stream(requestedKey.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
        if ((!owned && !defaultAvatar) || invalidPath) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인의 프로필 사진 키를 사용해주세요.");
        }
        return requestedKey;
    }

    private Team favoriteTeam(Integer teamId, LeagueCode league) {
        if (teamId == null) return null;
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "응원팀을 찾을 수 없습니다."));
        if (team.getLeague().getCode() != league || !team.isOnboardingSelectable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 리그에서 선택 가능한 응원팀이 아닙니다.");
        }
        return team;
    }

    private MyProfileResponse response(Profile profile) {
        String avatarUrl = UriComponentsBuilder.fromUriString(assetsBaseUrl)
                .pathSegment(profile.getAvatarKey().split("/"))
                .build().encode().toUriString();
        return MyProfileResponse.from(profile, avatarUrl);
    }
}
