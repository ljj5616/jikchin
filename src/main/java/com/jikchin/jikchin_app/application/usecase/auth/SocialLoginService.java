package com.jikchin.jikchin_app.application.usecase.auth;

import com.jikchin.jikchin_app.application.port.in.auth.SocialLoginCommand;
import com.jikchin.jikchin_app.application.port.in.auth.SocialLoginResult;
import com.jikchin.jikchin_app.application.port.in.auth.SocialLoginUseCase;
import com.jikchin.jikchin_app.application.port.out.auth.OAuthProviderPort;
import com.jikchin.jikchin_app.application.port.out.auth.TokenProviderPort;
import com.jikchin.jikchin_app.application.port.out.user.UserRepositoryPort;
import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.domain.user.model.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService implements SocialLoginUseCase {

    private final OAuthProviderPort oAuthProviderPort;
    private final TokenProviderPort tokenProviderPort;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    @Transactional
    public SocialLoginResult login(SocialLoginCommand command) {
        final String provider = command.provider();
        final String providerId = oAuthProviderPort.verifyAndGetProviderId(provider, command.accessToken());

        User user = userRepositoryPort.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepositoryPort.save(User.createSocialUser(provider, providerId)));

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
