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
import com.jikchin.jikchin_app.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompleteProfileServiceTest {

    @Mock UserRepository users;
    @Mock ProfileRepository profiles;
    @Mock TeamRepository teams;
    @Mock TokenProviderPort tokens;

    private CompleteProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CompleteProfileService(users, profiles, teams, tokens);
        ReflectionTestUtils.setField(service, "baseUrl", "https://assets.example.test");
        ReflectionTestUtils.setField(service, "defaultKey", "avatars/defaults/default1.jpg");
        user = User.createSocialUser("google", "provider-id");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void completingProfileUsesDefaultAvatarAndActivatesUser() {
        stubTokens();

        CompleteProfileResult result = service.completeProfile(command(null, null, null));

        Profile profile = savedProfile();
        assertThat(profile.getNickname()).isEqualTo("mate");
        assertThat(profile.getAvatarKey()).isEqualTo("avatars/defaults/default1.jpg");
        assertThat(profile.getBio()).isEqualTo("baseball fan");
        assertThat(user.getProfile()).isSameAs(profile);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result).isEqualTo(new CompleteProfileResult("access-token", "refresh-token"));
    }

    @Test
    void completingProfilePreservesSelectedTeamsAndConvertsOwnAvatarUrlToKey() {
        Team kbo = mock(Team.class);
        Team kleague = mock(Team.class);
        when(teams.findById(1)).thenReturn(Optional.of(kbo));
        when(teams.findById(2)).thenReturn(Optional.of(kleague));
        stubTokens();

        service.completeProfile(command("https://assets.example.test/avatars/users/7/avatar.jpg", 1, 2));

        Profile profile = savedProfile();
        assertThat(profile.getAvatarKey()).isEqualTo("avatars/users/7/avatar.jpg");
        assertThat(profile.getFavoriteKboTeam()).isSameAs(kbo);
        assertThat(profile.getFavoriteKleagueTeam()).isSameAs(kleague);
    }

    @Test
    void duplicateNicknameDoesNotSaveProfileOrIssueTokens() {
        when(profiles.existsByNickname("mate")).thenReturn(true);

        assertThatThrownBy(() -> service.completeProfile(command(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("nickname already taken");

        verify(profiles, never()).save(any(Profile.class));
        verifyNoInteractions(tokens);
        assertThat(user.getStatus()).isEqualTo(UserStatus.REGISTERING);
    }

    @Test
    void avatarOwnedByAnotherUserIsRejected() {
        assertThatThrownBy(() -> service.completeProfile(command("avatars/users/8/avatar.jpg", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid avatar key");

        verify(profiles, never()).save(any(Profile.class));
        verifyNoInteractions(tokens);
        assertThat(user.getStatus()).isEqualTo(UserStatus.REGISTERING);
    }

    @Test
    void unknownKboTeamIsRejectedBeforeProfileIsSaved() {
        when(teams.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeProfile(command(null, 99, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KBO team not found");

        verify(profiles, never()).save(any(Profile.class));
        verifyNoInteractions(tokens);
    }

    private CompleteProfileCommand command(String avatar, Integer kboId, Integer kleagueId) {
        return new CompleteProfileCommand(7L, "mate", avatar, "baseball fan", kboId, kleagueId);
    }

    private void stubTokens() {
        when(tokens.issueAccessToken(7L, false)).thenReturn("access-token");
        when(tokens.issueRefreshToken(7L)).thenReturn("refresh-token");
    }

    private Profile savedProfile() {
        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(captor.capture());
        return captor.getValue();
    }
}
