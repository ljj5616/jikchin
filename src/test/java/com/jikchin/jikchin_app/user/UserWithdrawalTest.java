package com.jikchin.jikchin_app.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.infrastructure.jwt.JwtTokenProvider;
import com.jikchin.jikchin_app.auth.port.OAuthProviderPort;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.auth.service.RefreshSessionService;
import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import com.jikchin.jikchin_app.chat.domain.ChatThread;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import com.jikchin.jikchin_app.game.domain.Game;
import com.jikchin.jikchin_app.moderation.domain.ReportReason;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;
import com.jikchin.jikchin_app.moderation.dto.SubmitReportRequest;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.moderation.service.ReportService;
import com.jikchin.jikchin_app.point.domain.PointAccount;
import com.jikchin.jikchin_app.record.domain.Record;
import com.jikchin.jikchin_app.record.domain.RecordImage;
import com.jikchin.jikchin_app.record.domain.RecordVisibility;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.service.WithdrawUserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:withdrawal-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserWithdrawalTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TokenProviderPort tokens;
    @Autowired RefreshSessionService sessions;
    @Autowired WithdrawUserService withdrawal;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired RoomChatService chat;
    @Autowired BlockService blocking;
    @Autowired ReportService reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean OAuthProviderPort oauth;

    @Test
    void deletesProfileAndAllSessionsAndSameSocialIdentityCanRegisterAsANewUser() throws Exception {
        var user = member("기존회원");
        String providerId = user.getProviderId();
        String access = bearer(user);
        List<String> refreshTokens = List.of(sessions.issue(user), sessions.issue(user));

        mvc.perform(delete("/users/me").header("Authorization", access))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        em.clear();

        var deleted = users.findById(user.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(deleted.getProvider()).isEqualTo("deleted");
        assertThat(deleted.getProviderId()).isNotEqualTo(providerId);
        assertThat(profiles.existsById(user.getId())).isFalse();
        assertThat(count("auth_refresh_session", "user_id", user.getId())).isZero();
        for (String path : List.of("/users/me", "/users/me/profile", "/auth/session")) {
            mvc.perform(get(path).header("Authorization", access)).andExpect(status().isUnauthorized());
        }
        mvc.perform(delete("/users/me").header("Authorization", access)).andExpect(status().isUnauthorized());
        for (String refresh : refreshTokens) {
            mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                    .andExpect(status().isUnauthorized());
        }

        when(oauth.verifyAndGetProviderId("google", "social-token")).thenReturn(providerId);
        var login = json.readTree(mvc.perform(post("/auth/google/login").contentType(APPLICATION_JSON)
                        .content("{\"accessToken\":\"social-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.needProfile").value(true))
                .andExpect(jsonPath("$.refreshToken").isEmpty()).andReturn().getResponse().getContentAsString());
        Long newId = tokens.parse(login.get("accessToken").asText()).userId();
        assertThat(newId).isNotEqualTo(user.getId());
        mvc.perform(post("/auth/complete-profile").header("Authorization", "Bearer " + login.get("accessToken").asText())
                        .contentType(APPLICATION_JSON).content("{\"nickname\":\"기존회원\"}"))
                .andExpect(status().isOk());
        assertThat(profiles.findById(newId).orElseThrow().getNickname()).isEqualTo("기존회원");
        mvc.perform(get("/users/me").header("Authorization", access)).andExpect(status().isUnauthorized());
    }

    @Test
    void leavesEveryRoomTransfersHostAndPreservesAnonymizedSharedChatAndReports() throws Exception {
        var user = member("탈퇴예정");
        var first = member("먼저입장");
        var second = member("나중입장");
        int[] teams = teams();
        var hosted = room(user, teams);
        joinRooms.join(first.getId(), hosted.id());
        joinRooms.join(second.getId(), hosted.id());
        var alone = room(user, teams);
        var guestRoom = room(first, teams);
        joinRooms.join(user.getId(), guestRoom.id());
        var direct = ChatThread.createDirect(user);
        em.persist(direct);
        em.persist(ChatParticipant.join(direct, user));
        em.persist(ChatParticipant.join(direct, first));
        var message = chat.send(user.getId(), hosted.id(), new SendMessageRequest("기존 대화", UUID.randomUUID().toString()));
        var report = reports.submit(first.getId(), ReportTargetType.USER, user.getId(),
                new SubmitReportRequest(ReportReason.ABUSE, "신고 증거", UUID.randomUUID().toString()));
        var roomReport = reports.submit(first.getId(), ReportTargetType.ROOM, alone.id(),
                new SubmitReportRequest(ReportReason.OTHER, null, UUID.randomUUID().toString()));
        blocking.block(user.getId(), first.getId());
        blocking.block(first.getId(), user.getId());
        blocking.block(first.getId(), second.getId());

        mvc.perform(delete("/users/me").header("Authorization", bearer(user))).andExpect(status().isNoContent());
        em.clear();

        assertThat(count("chat_participant", "user_id", user.getId())).isZero();
        assertThat(count("room", "id", alone.id())).isZero();
        assertThat(count("chat_thread", "id", alone.chatThreadId())).isZero();
        assertThat(jdbc.queryForObject("select host_id from room where id = ?", Long.class, hosted.id())).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject("select created_by_id from chat_thread where id = ?", Long.class, hosted.chatThreadId())).isEqualTo(user.getId());
        assertThat(count("chat_participant", "chat_thread_id", hosted.chatThreadId())).isEqualTo(2);
        assertThat(count("chat_participant", "chat_thread_id", guestRoom.chatThreadId())).isEqualTo(1);
        assertThat(count("chat_participant", "chat_thread_id", direct.getId())).isEqualTo(1);
        assertThat(count("block", "blocker_id", user.getId())).isZero();
        assertThat(count("block", "blocked_id", user.getId())).isZero();
        assertThat(count("block", "blocker_id", first.getId())).isEqualTo(1);
        assertThat(count("report", "id", report.report().id())).isEqualTo(1);
        assertThat(count("report", "id", roomReport.report().id())).isEqualTo(1);
        var history = chat.history(first.getId(), hosted.id(), null, 50).messages();
        assertThat(history).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo(message.id());
            assertThat(saved.body()).isEqualTo("기존 대화");
            assertThat(saved.senderNickname()).isEqualTo("탈퇴한 회원");
            assertThat(saved.senderAvatarUrl()).endsWith("/avatars/defaults/default1.jpg");
        });
        mvc.perform(get("/users/{id}/profile", user.getId()).header("Authorization", bearer(first)))
                .andExpect(status().isNotFound());
        // The departed creator does not prevent other active users from joining the surviving room.
        mvc.perform(post("/api/rooms/{id}/participants", hosted.id()).header("Authorization", bearer(member("새참여자"))))
                .andExpect(status().isOk());
    }

    @Test
    void removesAttendanceRecordsAndImagesWhileKeepingPointHistoryReferences() {
        var user = member("기록회원");
        var other = member("다른회원");
        int[] teams = teams();
        jdbc.update("insert into stadium (name, region) values ('잠실', 'JAMSIL')");
        int stadium = jdbc.queryForObject("select max(id) from stadium where name = '잠실'", Integer.class);
        jdbc.update("insert into game (season, start_at, league_id, home_team_id, away_team_id, stadium_id) values (?, ?, ?, ?, ?, ?)",
                2026, LocalDate.now().atStartOfDay(), teams[2], teams[0], teams[1], stadium);
        Long gameId = jdbc.queryForObject("select id from game where home_team_id = ?", Long.class, teams[0]);
        var record = Record.create(user, em.find(Game.class, gameId), "직관", "개인 기록", RecordVisibility.PUBLIC);
        record.addImage(RecordImage.of("https://assets.example.test/private.jpg"));
        em.persist(record);
        var otherRecord = Record.create(other, em.find(Game.class, gameId), "다른 기록", "보존", RecordVisibility.PUBLIC);
        em.persist(otherRecord);
        em.persist(PointAccount.open(user));
        em.flush();

        withdrawal.withdraw(user.getId());
        em.clear();

        assertThat(count("record", "user_id", user.getId())).isZero();
        assertThat(count("record_image", "record_id", record.getId())).isZero();
        assertThat(count("record", "id", otherRecord.getId())).isEqualTo(1);
        assertThat(count("point_account", "user_id", user.getId())).isEqualTo(1);
    }

    @Test
    void requiresAnActiveMembersAccessToken() throws Exception {
        var user = member("유지회원");
        var registering = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        var suspended = member("정지회원");
        String suspendedAccess = bearer(suspended);
        ReflectionTestUtils.setField(suspended, "status", UserStatus.SUSPENDED);
        users.flush();
        String expired = new JwtTokenProvider("jikchin-test-signing-key-for-isolated-tests-only", -1000, -1000)
                .issueAccessToken(user.getId(), false);
        mvc.perform(delete("/users/me")).andExpect(status().isUnauthorized());
        for (String token : List.of("invalid", sessions.issue(user), expired, suspendedAccess.substring(7))) {
            mvc.perform(delete("/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(delete("/users/me").header("Authorization", "Bearer " + tokens.issueAccessToken(registering.getId(), true)))
                .andExpect(status().isForbidden());
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(profiles.existsById(user.getId())).isTrue();
    }

    private User member(String nickname) {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, nickname, "avatars/users/" + user.getId() + "/photo.jpg", "자기소개"));
        return user;
    }

    private String bearer(User user) { return "Bearer " + tokens.issueAccessToken(user.getId(), false); }

    private int[] teams() {
        jdbc.update("insert into league(code) select 'KBO' where not exists (select 1 from league where code = 'KBO')");
        int league = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        String prefix = UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values (?, 'A', '', ?, true), (?, 'B', '', ?, true)", prefix + "A", league, prefix + "B", league);
        return new int[]{jdbc.queryForObject("select id from team where name = ?", Integer.class, prefix + "A"),
                jdbc.queryForObject("select id from team where name = ?", Integer.class, prefix + "B"), league};
    }

    private RoomResponse room(User host, int[] teams) {
        return createRooms.create(host.getId(), new CreateRoomRequest("직관방", LocalDate.now(), "잠실", teams[0], teams[1], 5));
    }

    private long count(String table, String column, Long id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Long.class, id);
    }
}
