package com.jikchin.jikchin_app.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.game.repository.TeamRepository;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.dto.UpdateMyProfileRequest;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MyProfileTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TeamRepository teams;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private int kboTeamId;
    private int kleagueTeamId;
    private int retiredTeamId;

    @BeforeEach
    void teams() {
        jdbc.update("insert into league (code) values ('KBO'), ('KLEAGUE')");
        int kbo = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        int kleague = jdbc.queryForObject("select id from league where code = 'KLEAGUE'", Integer.class);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values ('야구팀', '야구', '', ?, true), ('축구팀', '축구', '', ?, true), ('이전팀', '이전', '', ?, false)", kbo, kleague, kbo);
        kboTeamId = jdbc.queryForObject("select id from team where name = '야구팀'", Integer.class);
        kleagueTeamId = jdbc.queryForObject("select id from team where name = '축구팀'", Integer.class);
        retiredTeamId = jdbc.queryForObject("select id from team where name = '이전팀'", Integer.class);
    }

    @Test
    void readsOnlyTheAuthenticatedUsersProfileAndKeepsTheExistingMeContract() throws Exception {
        var me = member("내닉네임");
        member("다른회원");
        var profile = profiles.findById(me.getId()).orElseThrow();
        profile.updateProfile("내닉네임", "avatars/users/" + me.getId() + "/my photo.jpg", "같이 직관해요",
                teams.findById(kboTeamId).orElseThrow(), teams.findById(kleagueTeamId).orElseThrow());
        profiles.flush();
        em.clear();

        mvc.perform(get("/users/me/profile").header("Authorization", access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.getId()))
                .andExpect(jsonPath("$.nickname").value("내닉네임"))
                .andExpect(jsonPath("$.bio").value("같이 직관해요"))
                .andExpect(jsonPath("$.avatarKey").value("avatars/users/" + me.getId() + "/my photo.jpg"))
                .andExpect(jsonPath("$.avatarUrl").value("https://assets.example.test/avatars/users/" + me.getId() + "/my%20photo.jpg"))
                .andExpect(jsonPath("$.favoriteKboTeam.id").value(kboTeamId))
                .andExpect(jsonPath("$.favoriteKboTeam.name").value("야구팀"))
                .andExpect(jsonPath("$.favoriteKleagueTeam.id").value(kleagueTeamId));
        mvc.perform(get("/users/me").header("Authorization", access(me)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":" + me.getId() + ",\"nickname\":\"내닉네임\"}", org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void updatesAndPersistsAllEditableFieldsWithoutChangingAnotherMember() throws Exception {
        var me = member("내닉네임");
        var other = member("다른회원");
        String avatar = "avatars/users/" + me.getId() + "/new.jpg";
        var request = new UpdateMyProfileRequest(" 새닉네임 ", avatar, " 소개 변경 ", kboTeamId, kleagueTeamId);

        mvc.perform(put("/users/me/profile").header("Authorization", access(me))
                        .contentType(APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.getId()))
                .andExpect(jsonPath("$.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.bio").value("소개 변경"))
                .andExpect(jsonPath("$.avatarKey").value(avatar))
                .andExpect(jsonPath("$.favoriteKboTeam.id").value(kboTeamId))
                .andExpect(jsonPath("$.favoriteKleagueTeam.id").value(kleagueTeamId));
        em.clear();

        var saved = profiles.findById(me.getId()).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("새닉네임");
        assertThat(saved.getAvatarKey()).isEqualTo(avatar);
        assertThat(saved.getBio()).isEqualTo("소개 변경");
        assertThat(saved.getFavoriteKboTeam().getId()).isEqualTo(kboTeamId);
        assertThat(saved.getFavoriteKleagueTeam().getId()).isEqualTo(kleagueTeamId);
        assertThat(profiles.findById(other.getId()).orElseThrow().getNickname()).isEqualTo("다른회원");
        mvc.perform(get("/users/me/profile").header("Authorization", access(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("새닉네임"));
    }

    @Test
    void omittedOptionalFieldsResetThePhotoAndClearBioAndTeams() throws Exception {
        var me = member("내닉네임");
        profiles.findById(me.getId()).orElseThrow().updateProfile("내닉네임", "avatars/users/" + me.getId() + "/old.jpg",
                "소개", teams.findById(kboTeamId).orElseThrow(), teams.findById(kleagueTeamId).orElseThrow());
        profiles.flush();
        em.clear();

        mvc.perform(put("/users/me/profile").header("Authorization", access(me))
                        .contentType(APPLICATION_JSON).content("{\"nickname\":\"내닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarKey").value("avatars/defaults/default1.jpg"))
                .andExpect(jsonPath("$.avatarUrl").value("https://assets.example.test/avatars/defaults/default1.jpg"))
                .andExpect(jsonPath("$.bio").value(nullValue()))
                .andExpect(jsonPath("$.favoriteKboTeam").value(nullValue()))
                .andExpect(jsonPath("$.favoriteKleagueTeam").value(nullValue()));
        em.clear();
        var saved = profiles.findById(me.getId()).orElseThrow();
        assertThat(saved.getBio()).isNull();
        assertThat(saved.getFavoriteKboTeam()).isNull();
        assertThat(saved.getFavoriteKleagueTeam()).isNull();
    }

    @Test
    void nicknameAvailabilityExcludesSelfAndDuplicateUpdatesLeaveTheProfileUntouched() throws Exception {
        var me = member("내닉네임");
        member("다른회원");
        for (String nickname : List.of("내닉네임", " 내닉네임 ", "사용가능")) {
            mvc.perform(get("/users/me/nickname-availability").param("nickname", nickname).header("Authorization", access(me)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        }
        mvc.perform(get("/users/me/nickname-availability").param("nickname", " 다른회원 ").header("Authorization", access(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(put("/users/me/profile").header("Authorization", access(me)).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new UpdateMyProfileRequest("다른회원", null, "덮어쓰기", null, null))))
                .andExpect(status().isConflict());
        assertThat(profiles.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("내닉네임");
        assertThat(profiles.findById(me.getId()).orElseThrow().getBio()).isEqualTo("기존 소개");
    }

    @Test
    void rejectsInvalidNicknamesAndFieldLengths() throws Exception {
        var me = member("내닉네임");
        for (String nickname : List.of("", "  ", "a".repeat(21))) {
            mvc.perform(get("/users/me/nickname-availability").param("nickname", nickname).header("Authorization", access(me)))
                    .andExpect(status().isBadRequest());
        }
        List<UpdateMyProfileRequest> invalidRequests = List.of(
                new UpdateMyProfileRequest(null, null, null, null, null),
                new UpdateMyProfileRequest("  ", null, null, null, null),
                new UpdateMyProfileRequest("a".repeat(21), null, null, null, null),
                new UpdateMyProfileRequest("변경", "a".repeat(201), null, null, null),
                new UpdateMyProfileRequest("변경", null, "a".repeat(161), null, null),
                new UpdateMyProfileRequest("변경", null, null, 0, null),
                new UpdateMyProfileRequest("변경", null, null, null, -1));
        for (var request : invalidRequests) {
            mvc.perform(put("/users/me/profile").header("Authorization", access(me)).contentType(APPLICATION_JSON)
                            .content(json.writeValueAsBytes(request)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(profiles.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("내닉네임");
    }

    @Test
    void rejectsOtherUsersPhotosUrlsAndPathTraversalWithoutSaving() throws Exception {
        var me = member("내닉네임");
        for (String avatar : List.of("", "avatars/users/999999/photo.jpg", "https://example.com/photo.jpg",
                "avatars/users/" + me.getId() + "/../999/photo.jpg", "avatars/users/" + me.getId() + "/%2e%2e/photo.jpg",
                "avatars/defaults/../users/999/photo.jpg", "avatars/defaults/", "avatars/defaults/..\\photo.jpg")) {
            mvc.perform(put("/users/me/profile").header("Authorization", access(me)).contentType(APPLICATION_JSON)
                            .content(json.writeValueAsBytes(new UpdateMyProfileRequest("변경", avatar, null, null, null))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(profiles.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("내닉네임");
    }

    @Test
    void rejectsWrongLeagueRetiredAndMissingTeamsBeforeMutatingTheProfile() throws Exception {
        var me = member("내닉네임");
        for (var request : List.of(
                new UpdateMyProfileRequest("변경", null, null, kleagueTeamId, null),
                new UpdateMyProfileRequest("변경", null, null, null, kboTeamId),
                new UpdateMyProfileRequest("변경", null, null, retiredTeamId, null),
                new UpdateMyProfileRequest("변경", null, null, Integer.MAX_VALUE, null))) {
            mvc.perform(put("/users/me/profile").header("Authorization", access(me)).contentType(APPLICATION_JSON)
                            .content(json.writeValueAsBytes(request)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(profiles.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("내닉네임");
        assertThat(profiles.findById(me.getId()).orElseThrow().getBio()).isEqualTo("기존 소개");
    }

    @Test
    void allMyPageEndpointsRequireNormalAccessTokens() throws Exception {
        var onboarding = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        String onboardingToken = "Bearer " + tokens.issueAccessToken(onboarding.getId(), true);
        for (String authorization : List.of("", onboardingToken)) {
            int expectedStatus = authorization.isEmpty() ? 401 : 403;
            mvc.perform(get("/users/me/profile").header("Authorization", authorization))
                    .andExpect(status().is(expectedStatus));
            mvc.perform(put("/users/me/profile").header("Authorization", authorization).contentType(APPLICATION_JSON)
                            .content("{\"nickname\":\"변경\"}"))
                    .andExpect(status().is(expectedStatus));
            mvc.perform(get("/users/me/nickname-availability").header("Authorization", authorization).param("nickname", "변경"))
                    .andExpect(status().is(expectedStatus));
            mvc.perform(multipart("/users/me/avatar").file(image()).header("Authorization", authorization))
                    .andExpect(status().is(expectedStatus));
        }
    }

    @Test
    void membersWithoutProfilesGetNotFoundAndInvalidUploadsReturnBadRequest() throws Exception {
        var legacyMember = User.createSocialUser("google", UUID.randomUUID().toString());
        legacyMember.activate();
        users.saveAndFlush(legacyMember);
        mvc.perform(get("/users/me/profile").header("Authorization", access(legacyMember)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/users/me/profile").header("Authorization", access(legacyMember)).contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"변경\"}"))
                .andExpect(status().isNotFound());
        var me = member("내닉네임");
        mvc.perform(multipart("/users/me/avatar").file(image()).header("Authorization", access(me)))
                .andExpect(status().isBadRequest());
    }

    private User member(String nickname) {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, nickname, "avatars/defaults/default1.jpg", "기존 소개"));
        return user;
    }

    private String access(User user) {
        return "Bearer " + tokens.issueAccessToken(user.getId(), false);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "invalid.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
