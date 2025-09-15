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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteProfileService implements CompleteProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final ProfileRepositoryPort profileRepositoryPort;
    private final KboTeamQueryPort kboTeamQueryPort;
    private final KLeagueTeamQueryPort kLeagueTeamQueryPort;
    private final TokenProviderPort tokenProviderPort;

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

        Profile profile = Profile.create(
                user,
                request.getNickname(),
                request.getAvatarUrl(),
                request.getBio()
        );
        if (kboTeam != null || kleagueTeam != null) {
            profile.chooseFavoriteTeams(kboTeam, kleagueTeam);
        }

        profileRepositoryPort.save(profile);
        userRepositoryPort.save(user);

        String access = tokenProviderPort.issueAccessToken(user.getId(), false);
        String refresh = tokenProviderPort.issueRefreshToken(user.getId());

        return CompleteProfileResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .build();
    }
}
