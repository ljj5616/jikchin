package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.game.repository.TeamRepository;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.dto.UpdateMyProfileRequest;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyProfileServiceTest {
    @Mock ProfileRepository profiles;
    @Mock TeamRepository teams;

    @Test
    void nicknameClaimedAfterAvailabilityCheckStillReturnsConflict() {
        var service = new MyProfileService(profiles, teams);
        ReflectionTestUtils.setField(service, "defaultAvatarKey", "avatars/defaults/default1.jpg");
        var profile = Profile.create(User.createSocialUser("google", "id"), "기존닉네임", "avatars/defaults/default1.jpg", null);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));
        when(profiles.saveAndFlush(any(Profile.class))).thenThrow(new DataIntegrityViolationException("duplicate nickname"));

        assertThatThrownBy(() -> service.update(7L, new UpdateMyProfileRequest("새닉네임", null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
