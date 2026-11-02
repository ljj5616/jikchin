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
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @MockitoBean OAuthProviderPort oauth;

    @Test
    void onboardingCanListTeamsAndSaveOnlyTeamsFromTheCorrectLeague() throws Exception {
        jdbc.update("insert into league (code) values (?)", "KBO");
        jdbc.update("insert into league (code) values (?)", "KLEAGUE");
        int kboLeague = jdbc.queryForObject("select id from league where code = ?", Integer.class, "KBO");
        int footballLeague = jdbc.queryForObject("select id from league where code = ?", Integer.class, "KLEAGUE");
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values (?, ?, ?, ?, ?)", "야구팀", "야구", "", kboLeague, true);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values (?, ?, ?, ?, ?)", "축구팀", "축구", "", footballLeague, true);
        int kbo = jdbc.queryForObject("select id from team where name = ?", Integer.class, "야구팀");
        int football = jdbc.queryForObject("select id from team where name = ?", Integer.class, "축구팀");
        when(oauth.verifyAndGetProviderId("google", "team-token")).thenReturn("team-user");
        String token = postJson("/auth/google/login", "{\"accessToken\":\"team-token\"}").get("accessToken").asText();

        mvc.perform(get("/api/teams").param("league", "KBO"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/teams").param("league", "KBO").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(kbo)).andExpect(jsonPath("$[0].name").value("야구팀"));
        mvc.perform(get("/api/teams").param("league", "KLEAGUE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(football));
        mvc.perform(get("/api/teams").param("league", "INVALID").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/complete-profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"team-mate\",\"favoriteKboTeamId\":" + football + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/complete-profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"team-mate\",\"favoriteKboTeamId\":" + kbo +
                                ",\"favoriteKleagueTeamId\":" + football + "}"))
                .andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select favorite_kbo_team_id from profile where nickname = ?", Integer.class, "team-mate")).isEqualTo(kbo);
        assertThat(jdbc.queryForObject("select favorite_kleague_team_id from profile where nickname = ?", Integer.class, "team-mate")).isEqualTo(football);
    }

    @Test
    void nicknameAvailabilityRequiresOnboardingAndUsesProfileValidationRules() throws Exception {
        when(oauth.verifyAndGetProviderId("google", "first-token")).thenReturn("nickname-first");
        when(oauth.verifyAndGetProviderId("google", "second-token")).thenReturn("nickname-second");
        String first = postJson("/auth/google/login", "{\"accessToken\":\"first-token\"}").get("accessToken").asText();
        String second = postJson("/auth/google/login", "{\"accessToken\":\"second-token\"}").get("accessToken").asText();

        mvc.perform(get("/auth/nickname-availability").param("nickname", "mate"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/nickname-availability").header("Authorization", "Bearer " + first)
                        .param("nickname", "mate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/auth/nickname-availability").header("Authorization", "Bearer " + first))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/auth/nickname-availability").header("Authorization", "Bearer " + first)
                        .param("nickname", "   "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/auth/nickname-availability").header("Authorization", "Bearer " + first)
                        .param("nickname", "a".repeat(21)))
                .andExpect(status().isBadRequest());

        JsonNode profile = json.readTree(mvc.perform(post("/auth/complete-profile")
                        .header("Authorization", "Bearer " + first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"mate\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/auth/nickname-availability").header("Authorization", "Bearer " + second)
                        .param("nickname", "mate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/auth/nickname-availability")
                        .header("Authorization", "Bearer " + profile.get("accessToken").asText())
                        .param("nickname", "mate"))
                .andExpect(status().isForbidden());
    }

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
