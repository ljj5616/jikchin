package com.jikchin.jikchin_app.auth.service;

import com.jikchin.jikchin_app.auth.dto.SocialLoginCommand;
import com.jikchin.jikchin_app.auth.dto.SocialLoginResult;
import com.jikchin.jikchin_app.auth.port.OAuthProviderPort;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final OAuthProviderPort oAuthProviderPort;
    private final TokenProviderPort tokenProviderPort;
    private final UserRepository userRepository;

    @Transactional
    public SocialLoginResult login(SocialLoginCommand command) {
        final String provider = command.provider();
        final String providerId = oAuthProviderPort.verifyAndGetProviderId(provider, command.accessToken());

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.save(User.createSocialUser(provider, providerId)));

        if (user.getStatus() == UserStatus.REGISTERING) {
            // 온보딩 토큰 (needProfile == true)
            String onboardingAccess = tokenProviderPort.issueAccessToken(user.getId(), true);
            return new SocialLoginResult(true, onboardingAccess, null);
        } else {
            // 정상 로그인 토큰 발급
            String access = tokenProviderPort.issueAccessToken(user.getId(), false);
            String refresh = tokenProviderPort.issueRefreshToken(user.getId());
            return new SocialLoginResult(false, access, refresh);
        }
    }
}
