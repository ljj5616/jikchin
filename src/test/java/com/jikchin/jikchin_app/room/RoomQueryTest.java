package com.jikchin.jikchin_app.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomQueryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;

    private int homeTeamId;
    private int awayTeamId;

    @BeforeEach
    void teams() {
        jdbc.update("insert into league (code) values ('KBO')");
        int leagueId = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values ('A', 'A', '', ?, true), ('B', 'B', '', ?, true)", leagueId, leagueId);
        homeTeamId = jdbc.queryForObject("select id from team where name = 'A'", Integer.class);
        awayTeamId = jdbc.queryForObject("select id from team where name = 'B'", Integer.class);
    }

    @Test
    void activeUsersCanPreviewRoomAndParticipantsWithoutJoining() throws Exception {
        var host = userWithProfile("방장", null);
        var guest = userWithProfile("동행자", "photo.jpg");
        var viewer = userWithProfile("구경하는 사람", null);
        var room = room(host, 4);
        joinRooms.join(guest.getId(), room.id());
        em.flush();
        em.clear();

        var expected = json.valueToTree(room);
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).put("participantCount", 2);
        for (var user : List.of(host, guest, viewer)) {
            mvc.perform(get("/api/rooms/{roomId}", room.id()).header("Authorization", access(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().json(json.writeValueAsString(expected), STRICT));
        }
        mvc.perform(get("/api/rooms/{roomId}/participants", room.id()).header("Authorization", access(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(host.getId()))
                .andExpect(jsonPath("$[0].nickname").value("방장"))
                .andExpect(jsonPath("$[0].avatarUrl").value("https://assets.example.test/avatars/defaults/default1.jpg"))
                .andExpect(jsonPath("$[0].host").value(true))
                .andExpect(jsonPath("$[0].joinedAt").isString())
                .andExpect(jsonPath("$[1].userId").value(guest.getId()))
                .andExpect(jsonPath("$[1].nickname").value("동행자"))
                .andExpect(jsonPath("$[1].avatarUrl").value("https://assets.example.test/avatars/users/" + guest.getId() + "/photo.jpg"))
                .andExpect(jsonPath("$[1].host").value(false))
                .andExpect(jsonPath("$[1].joinedAt").isString())
                .andExpect(jsonPath("$[0].provider").doesNotExist())
                .andExpect(jsonPath("$[0].providerId").doesNotExist())
                .andExpect(jsonPath("$[0].birthYear").doesNotExist())
                .andExpect(jsonPath("$[0].gender").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from chat_participant", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from chat_participant where user_id = ?",
                Integer.class, viewer.getId())).isZero();
    }

    @Test
    void participantListIsScopedToRoomAndOrderedByJoinTimeThenMembershipId() throws Exception {
        var host = userWithProfile("방장", null);
        var first = userWithProfile("첫번째", null);
        var second = userWithProfile("두번째", null);
        var unrelated = userWithProfile("다른 방 참여자", null);
        var room = room(host, 4);
        var otherRoom = room(host, 4);
        joinRooms.join(first.getId(), room.id());
        joinRooms.join(second.getId(), room.id());
        joinRooms.join(unrelated.getId(), otherRoom.id());
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0);
        jdbc.update("update chat_participant set joined_at = ? where chat_thread_id = ? and user_id = ?",
                time.plusSeconds(1), room.chatThreadId(), host.getId());
        jdbc.update("update chat_participant set joined_at = ? where chat_thread_id = ? and user_id <> ?",
                time, room.chatThreadId(), host.getId());
        em.clear();

        mvc.perform(get("/api/rooms/{roomId}/participants", room.id()).header("Authorization", access(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].userId").value(first.getId()))
                .andExpect(jsonPath("$[1].userId").value(second.getId()))
                .andExpect(jsonPath("$[2].userId").value(host.getId()))
                .andExpect(jsonPath("$[2].host").value(true));
        mvc.perform(get("/api/rooms/{roomId}/participants", otherRoom.id()).header("Authorization", access(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].userId").value(unrelated.getId()));
    }

    @Test
    void fullRoomsAndPastGamesRemainReadable() throws Exception {
        var host = userWithProfile("방장", null);
        var guest = userWithProfile("동행자", null);
        var room = room(host, 2);
        joinRooms.join(guest.getId(), room.id());
        LocalDate yesterday = LocalDate.now().minusDays(1);
        jdbc.update("update room set game_date = ? where id = ?", yesterday, room.id());
        em.clear();

        mvc.perform(get("/api/rooms/{roomId}", room.id()).header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.participantCount").value(2));
        mvc.perform(get("/api/rooms/{roomId}/participants", room.id()).header("Authorization", access(guest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsMissingRoomsAndInvalidRoomIds() throws Exception {
        var viewer = userWithProfile("조회자", null);
        for (String suffix : List.of("", "/participants")) {
            mvc.perform(get("/api/rooms/" + Long.MAX_VALUE + suffix).header("Authorization", access(viewer)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/rooms/not-a-number" + suffix).header("Authorization", access(viewer)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void requiresAnActiveUsersAccessTokenForBothEndpoints() throws Exception {
        var host = userWithProfile("방장", null);
        var room = room(host, 4);
        var onboarding = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        String onboardingAccess = "Bearer " + tokens.issueAccessToken(onboarding.getId(), true);
        for (String suffix : List.of("", "/participants")) {
            String path = "/api/rooms/" + room.id() + suffix;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", onboardingAccess)).andExpect(status().isForbidden());
        }
    }

    @Test
    void missingProfilesKeepMembershipVisibleAndAvatarUrlsEncodePathCharacters() throws Exception {
        var legacyHost = User.createSocialUser("google", UUID.randomUUID().toString());
        legacyHost.activate();
        users.saveAndFlush(legacyHost);
        var guest = userWithProfile("동행자", "game photo#1?.jpg");
        var room = room(legacyHost, 4);
        joinRooms.join(guest.getId(), room.id());
        em.flush();
        em.clear();

        mvc.perform(get("/api/rooms/{roomId}/participants", room.id()).header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(legacyHost.getId()))
                .andExpect(jsonPath("$[0].nickname").value(nullValue()))
                .andExpect(jsonPath("$[0].avatarUrl").value("https://assets.example.test/avatars/defaults/default1.jpg"))
                .andExpect(jsonPath("$[0].host").value(true))
                .andExpect(jsonPath("$[1].avatarUrl").value("https://assets.example.test/avatars/users/" + guest.getId() + "/game%20photo%231%3F.jpg"));
    }

    private User userWithProfile(String nickname, String filename) {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        String avatarKey = filename == null ? "avatars/defaults/default1.jpg" : "avatars/users/" + user.getId() + "/" + filename;
        profiles.saveAndFlush(Profile.create(user, nickname, avatarKey, "같이 직관해요"));
        return user;
    }

    private RoomResponse room(User host, int capacity) {
        return createRooms.create(host.getId(), new CreateRoomRequest("직관 가요", LocalDate.now(), "잠실",
                homeTeamId, awayTeamId, capacity));
    }

    private String access(User user) {
        return "Bearer " + tokens.issueAccessToken(user.getId(), false);
    }
}
