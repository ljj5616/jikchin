package com.jikchin.jikchin_app.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.OAuthProviderPort;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TokenProviderPort tokens;
    @MockitoBean OAuthProviderPort oauth;

    @Test
    void onboardingRefreshRotationAndLogout() throws Exception {
        when(oauth.verifyAndGetProviderId("google", "provider-token")).thenReturn("flow-test-id");
        JsonNode login = postJson("/auth/google/login", "{\"accessToken\":\"provider-token\"}");
        String onboarding = login.get("accessToken").asText();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + onboarding)).andExpect(status().isForbidden());
        JsonNode profile = json.readTree(mvc.perform(post("/auth/complete-profile")
                        .header("Authorization", "Bearer " + onboarding)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"flow-mate\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String access = profile.get("accessToken").asText();
        String refresh = profile.get("refreshToken").asText();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("flow-mate"));
        mvc.perform(get("/auth/session").header("Authorization", "Bearer " + onboarding)).andExpect(status().isUnauthorized());
        JsonNode rotated = postJson("/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
        String next = rotated.get("refreshToken").asText();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + next)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + next + "\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + next + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedAccountCannotUseExistingAccessTokenOrLogin() throws Exception {
        User user = User.createSocialUser("kakao", "suspended-test-id");
        user.activate();
        users.saveAndFlush(user);
        String access = tokens.issueAccessToken(user.getId(), false);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        users.saveAndFlush(user);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        when(oauth.verifyAndGetProviderId("kakao", "provider-token")).thenReturn("suspended-test-id");
        mvc.perform(post("/auth/kakao/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessToken\":\"provider-token\"}")).andExpect(status().isForbidden());
    }

    private JsonNode postJson(String path, String body) throws Exception {
        return json.readTree(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
