package com.jikchin.jikchin_app.auth.service;

import com.jikchin.jikchin_app.auth.dto.SocialLoginCommand;
import com.jikchin.jikchin_app.auth.dto.SocialLoginResult;
import com.jikchin.jikchin_app.auth.port.OAuthProviderPort;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    @Mock OAuthProviderPort oauth;
    @Mock TokenProviderPort tokens;
    @Mock UserRepository users;

    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(oauth, tokens, users);
    }

    @Test
    void newUserReceivesOnlyAnOnboardingAccessToken() {
        when(oauth.verifyAndGetProviderId("google", "social-token")).thenReturn("provider-id");
        when(users.findByProviderAndProviderId("google", "provider-id")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 7L);
            return user;
        });
        when(tokens.issueAccessToken(7L, true)).thenReturn("onboarding-token");

        SocialLoginResult result = service.login(new SocialLoginCommand("google", "social-token"));

        assertThat(result).isEqualTo(new SocialLoginResult(true, "onboarding-token", null));
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(savedUser.capture());
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.REGISTERING);
        assertThat(savedUser.getValue().getProviderId()).isEqualTo("provider-id");
        verify(tokens, never()).issueRefreshToken(any());
    }

    @Test
    void activeUserReceivesAccessAndRefreshTokensWithoutCreatingAnotherUser() {
        User user = User.createSocialUser("kakao", "provider-id");
        ReflectionTestUtils.setField(user, "id", 7L);
        user.activate();
        when(oauth.verifyAndGetProviderId("kakao", "social-token")).thenReturn("provider-id");
        when(users.findByProviderAndProviderId("kakao", "provider-id")).thenReturn(Optional.of(user));
        when(tokens.issueAccessToken(7L, false)).thenReturn("access-token");
        when(tokens.issueRefreshToken(7L)).thenReturn("refresh-token");

        SocialLoginResult result = service.login(new SocialLoginCommand("kakao", "social-token"));

        assertThat(result).isEqualTo(new SocialLoginResult(false, "access-token", "refresh-token"));
        verify(users, never()).save(any(User.class));
    }
}
