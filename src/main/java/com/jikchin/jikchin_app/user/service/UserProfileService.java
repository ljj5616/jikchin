package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.moderation.service.BlockedNickname;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.dto.MyProfileResponse.FavoriteTeamResponse;
import com.jikchin.jikchin_app.user.dto.UserProfileResponse;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final ProfileRepository profiles;
    private final BlockService blocks;

    @Value("${app.assets.base-url}") private String assetsBaseUrl;

    @Transactional(readOnly = true)
    public UserProfileResponse get(Long viewerId, Long userId) {
        var profile = profiles.findById(userId).filter(p -> p.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."));
        String avatarUrl = UriComponentsBuilder.fromUriString(assetsBaseUrl)
                .pathSegment(profile.getAvatarKey().split("/")).build().encode().toUriString();
        boolean blocked = blocks.blockedUserIds(viewerId).contains(userId);
        return new UserProfileResponse(userId, BlockedNickname.format(profile.getNickname(), blocked), avatarUrl,
                profile.getBio(), FavoriteTeamResponse.from(profile.getFavoriteKboTeam()),
                FavoriteTeamResponse.from(profile.getFavoriteKleagueTeam()), blocked);
    }
}
