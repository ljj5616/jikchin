package com.jikchin.jikchin_app;

import com.jikchin.jikchin_app.auth.controller.AuthController;
import com.jikchin.jikchin_app.auth.converter.OAuthProviderConverter;
import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.user.controller.UserController;
import com.jikchin.jikchin_app.auth.dto.SocialLoginCommand;
import com.jikchin.jikchin_app.auth.dto.SocialLoginResult;
import com.jikchin.jikchin_app.auth.service.SocialLoginService;
import com.jikchin.jikchin_app.user.dto.CompleteProfileCommand;
import com.jikchin.jikchin_app.user.dto.CompleteProfileResult;
import com.jikchin.jikchin_app.user.service.CompleteProfileService;
import com.jikchin.jikchin_app.user.service.GetUserService;
import com.jikchin.jikchin_app.user.dto.UserResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiContractTest {

    @Mock SocialLoginService login;
    @Mock CompleteProfileService profiles;
    @Mock GetUserService users;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var conversion = new DefaultFormattingConversionService();
        conversion.addConverter(new OAuthProviderConverter());
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, profiles), new UserController(users))
                .setConversionService(conversion)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void socialLoginKeepsProviderPathAndResponseFields() throws Exception {
        when(login.login(new SocialLoginCommand("google", "social-token")))
                .thenReturn(new SocialLoginResult(true, "onboarding-token", null));

        mvc.perform(post("/auth/google/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"accessToken\":\"social-token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"needProfile":true,"accessToken":"onboarding-token","refreshToken":null}
                        """, STRICT));
    }

    @Test
    void blankSocialTokenIsStillRejectedByRequestValidation() throws Exception {
        mvc.perform(post("/auth/google/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"accessToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(login);
    }

    @Test
    void completingProfileKeepsExistingRequestAndTwoTokenResponse() throws Exception {
        authenticate(true);
        when(profiles.completeProfile(new CompleteProfileCommand(7L, "mate", null, null, null, null)))
                .thenReturn(new CompleteProfileResult("access-token", "refresh-token"));

        mvc.perform(post("/auth/complete-profile")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"mate\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"accessToken":"access-token","refreshToken":"refresh-token"}
                        """, STRICT));
    }

    @Test
    void normalAccessTokenCannotCompleteOnboarding() throws Exception {
        authenticate(false);

        mvc.perform(post("/auth/complete-profile")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"mate\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profiles);
    }

    @Test
    void currentUserKeepsExistingPathAndResponseFields() throws Exception {
        authenticate(false);
        when(users.getById(7L)).thenReturn(new UserResult(7L, "mate"));

        mvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":7,\"nickname\":\"mate\"}", STRICT));
    }

    private void authenticate(boolean needProfile) {
        var principal = new CurrentUser(7L, needProfile, "access");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
