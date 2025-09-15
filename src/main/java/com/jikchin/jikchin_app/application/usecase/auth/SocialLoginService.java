package com.jikchin.jikchin_app.application.usecase.auth;

import com.jikchin.jikchin_app.application.dto.auth.SocialLoginRequest;
import com.jikchin.jikchin_app.application.dto.auth.SocialLoginResponse;
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
    public SocialLoginResponse login(SocialLoginRequest request) {
        final String provider = request.getProvider();
        final String providerId = oAuthProviderPort.verifyAndGetProviderId(provider, request.getAccessToken());

        User user = userRepositoryPort.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepositoryPort.save(User.createSocialUser(provider, providerId)));

        if (user.getStatus() == UserStatus.REGISTERING) {
            // 온보딩 토큰 (needProfile == true)
            String onboardingAccess = tokenProviderPort.issueAccessToken(user.getId(), true);
            return SocialLoginResponse.builder()
                    .needProfile(true)
                    .accessToken(onboardingAccess)
                    .refreshToken(null)
                    .build();
        } else {
            // 정상 로그인 토큰 발급
            String access = tokenProviderPort.issueAccessToken(user.getId(), false);
            String refresh = tokenProviderPort.issueRefreshToken(user.getId());
            return SocialLoginResponse.builder()
                    .needProfile(false)
                    .accessToken(access)
                    .refreshToken(refresh)
                    .build();
        }
    }
}
