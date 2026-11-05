package com.jikchin.jikchin_app.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.moderation.domain.Report;
import com.jikchin.jikchin_app.moderation.domain.ReportReason;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;
import com.jikchin.jikchin_app.moderation.dto.SubmitReportRequest;
import com.jikchin.jikchin_app.moderation.repository.ReportRepository;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.moderation.service.ReportService;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired ReportRepository reports;
    @Autowired ReportService reporting;
    @Autowired BlockService blocking;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired LeaveRoomService leaveRooms;
    private User reporter;
    private User target;
    private RoomResponse room;

    @BeforeEach
    void fixture() {
        reporter = user("신고자");
        target = user("상대방");
        jdbc.update("insert into league (code) values ('KBO')");
        int league = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        jdbc.update("insert into team(name,short_name,logo_url,league_id,onboarding_selectable) values ('A','A','',?,true),('B','B','',?,true)", league, league);
        int home = jdbc.queryForObject("select id from team where name = 'A'", Integer.class);
        int away = jdbc.queryForObject("select id from team where name = 'B'", Integer.class);
        room = createRooms.create(target.getId(), new CreateRoomRequest("모집방", LocalDate.now(), "잠실", home, away, 5));
    }

    @Test
    void reasonCatalogsExposeLabelsAndDifferentDetailRequirements() throws Exception {
        mvc.perform(get("/api/reports/reasons").param("targetType", "USER").header("Authorization", access(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.descriptionRequired").value(true)).andExpect(jsonPath("$.descriptionMaxLength").value(2000))
                .andExpect(jsonPath("$.reasons[*].code", hasItem("IMPERSONATION")))
                .andExpect(jsonPath("$.reasons[*].code", not(hasItem("MISLEADING_ROOM_INFO"))))
                .andExpect(jsonPath("$.reasons[0].label").value("욕설·비방·혐오 표현"));
        mvc.perform(get("/api/reports/reasons").param("targetType", "ROOM").header("Authorization", access(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descriptionRequired").value(false))
                .andExpect(jsonPath("$.reasons[*].code", hasItem("MISLEADING_ROOM_INFO")))
                .andExpect(jsonPath("$.reasons[*].code", not(hasItem("INAPPROPRIATE_PROFILE"))));
        for (String type : List.of("", "POST", "user")) {
            mvc.perform(get("/api/reports/reasons").param("targetType", type).header("Authorization", access(reporter)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void userReportUsesAuthenticatedReporterAndStoresTrimmedDetailsAndOriginalProfileSnapshot() throws Exception {
        String payload = json.writeValueAsString(Map.of("reason", "HARASSMENT", "description", "  반복적으로 위협했어요 🏟️  ",
                "reporterId", target.getId(), "status", "RESOLVED"));
        var result = mvc.perform(post(userPath(target.getId())).header("Authorization", access(reporter))
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.targetType").value("USER"))
                .andExpect(jsonPath("$.targetId").value(target.getId())).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value("HARASSMENT")).andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.targetSnapshot").doesNotExist()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        em.flush();
        em.clear();
        Report saved = reports.findById(id).orElseThrow();
        assertThat(saved.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(saved.getDescription()).isEqualTo("반복적으로 위협했어요 🏟️");
        assertThat(json.readTree(saved.getTargetSnapshot()).get("nickname").asText()).isEqualTo("상대방");
        jdbc.update("update profile set nickname = '이름변경' where user_id = ?", target.getId());
        assertThat(json.readTree(saved.getTargetSnapshot()).get("nickname").asText()).isEqualTo("상대방");
    }

    @Test
    void roomReportsWorkBeforeAndAfterJoiningAndAllowOmittedOrBlankDetails() throws Exception {
        for (String description : List.of("", "   ", "추가 설명")) {
            mvc.perform(post(roomPath(room.id())).header("Authorization", access(reporter)).contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(new SubmitReportRequest(ReportReason.MISLEADING_ROOM_INFO, description, null))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.targetType").value("ROOM"));
        }
        assertThat(jdbc.queryForObject("select count(*) from chat_participant where user_id = ?", Integer.class, reporter.getId())).isZero();
        joinRooms.join(reporter.getId(), room.id());
        mvc.perform(post(roomPath(room.id())).header("Authorization", access(reporter)).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isCreated());
        assertThat(reports.findAll()).hasSize(4).filteredOn(report -> report.getDescription() == null).hasSize(3);
    }

    @Test
    void roomReportSurvivesDeletionAndRetryStillReturnsTheOriginalReceipt() throws Exception {
        String key = UUID.randomUUID().toString();
        var submitted = reporting.submit(reporter.getId(), ReportTargetType.ROOM, room.id(),
                new SubmitReportRequest(ReportReason.FRAUD, null, key));
        leaveRooms.leave(target.getId(), room.id());
        em.flush();
        em.clear();
        assertThat(jdbc.queryForObject("select count(*) from room where id = ?", Integer.class, room.id())).isZero();
        var report = reports.findById(submitted.report().id()).orElseThrow();
        var snapshot = json.readTree(report.getTargetSnapshot());
        assertThat(snapshot.get("title").asText()).isEqualTo("모집방");
        assertThat(snapshot.get("creatorId").asLong()).isEqualTo(target.getId());
        assertThat(snapshot.get("homeTeam").asText()).isEqualTo("A");
        mvc.perform(post(roomPath(room.id())).header("Authorization", access(reporter)).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(new SubmitReportRequest(ReportReason.FRAUD, null, key))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(report.getId()));
        submit(roomPath(room.id()), "{\"reason\":\"FRAUD\"}", 404);
    }

    @Test
    void aRetryKeyIsScopedToReporterAndCannotBeReusedForDifferentContentOrTarget() throws Exception {
        String key = UUID.randomUUID().toString();
        String request = json.writeValueAsString(new SubmitReportRequest(ReportReason.ABUSE, "내용", key.toUpperCase()));
        submit(userPath(target.getId()), request, 201);
        submit(userPath(target.getId()), json.writeValueAsString(new SubmitReportRequest(ReportReason.ABUSE, " 내용 ", key)), 200);
        assertThat(reports.count()).isEqualTo(1);
        submit(userPath(target.getId()), json.writeValueAsString(new SubmitReportRequest(ReportReason.ABUSE, "다른 내용", key)), 409);
        submit(roomPath(room.id()), request, 409);
        var other = user("다른 신고자");
        mvc.perform(post(userPath(target.getId())).header("Authorization", access(other)).contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        assertThat(reports.count()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidReasonsDetailsAndTargetsWithoutCreatingReports() throws Exception {
        for (String payload : List.of("{}", "{\"reason\":\"NOT_A_REASON\",\"description\":\"내용\"}",
                "{\"reason\":\"ABUSE\"}", "{\"reason\":\"ABUSE\",\"description\":\"  \\n \\t\"}",
                "{\"reason\":\"MISLEADING_ROOM_INFO\",\"description\":\"내용\"}",
                "{\"reason\":\"ABUSE\",\"description\":\"내용\",\"clientReportId\":\"invalid\"}",
                json.writeValueAsString(new SubmitReportRequest(ReportReason.ABUSE, "가".repeat(2001), null)))) {
            submit(userPath(target.getId()), payload, 400);
        }
        submit(roomPath(room.id()), "{\"reason\":\"IMPERSONATION\"}", 400);
        submit(roomPath(room.id()), json.writeValueAsString(new SubmitReportRequest(ReportReason.OTHER, "x".repeat(2001), null)), 400);
        submit(userPath(reporter.getId()), "{\"reason\":\"ABUSE\",\"description\":\"내용\"}", 400);
        mvc.perform(post(roomPath(room.id())).header("Authorization", access(target)).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
        submit(userPath(0L), "{\"reason\":\"ABUSE\",\"description\":\"내용\"}", 400);
        submit(userPath(Long.MAX_VALUE), "{\"reason\":\"ABUSE\",\"description\":\"내용\"}", 404);
        submit(roomPath(Long.MAX_VALUE), "{\"reason\":\"SPAM\"}", 404);
        assertThat(reports.count()).isZero();
    }

    @Test
    void reportingDoesNotBlockExpelOrSuspendUsersAndBlockedTargetsCanStillBeReported() throws Exception {
        blocking.block(reporter.getId(), target.getId());
        submit(userPath(target.getId()), "{\"reason\":\"FRAUD\",\"description\":\"신고 내용\"}", 201);
        submit(roomPath(room.id()), "{\"reason\":\"FRAUD\"}", 201);
        assertThat(jdbc.queryForObject("select count(*) from block", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from user where id = ?", String.class, target.getId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from chat_participant", Integer.class)).isEqualTo(1);
    }

    @Test
    void publicProfileShowsOnlyPublicFieldsAndAppliesTheViewersBlockState() throws Exception {
        profiles.findById(target.getId()).orElseThrow().chooseFavoriteTeams(
                em.find(com.jikchin.jikchin_app.game.domain.Team.class, room.homeTeamId()), null);
        for (User viewer : List.of(reporter, target)) {
            mvc.perform(get(profilePath(target.getId())).header("Authorization", access(viewer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("상대방"))
                    .andExpect(jsonPath("$.bio").value("같이 직관해요"))
                    .andExpect(jsonPath("$.favoriteKboTeam.name").value("A"))
                    .andExpect(jsonPath("$.blocked").value(false)).andExpect(jsonPath("$.providerId").doesNotExist())
                    .andExpect(jsonPath("$.avatarKey").doesNotExist()).andExpect(jsonPath("$.birthYear").doesNotExist());
        }
        blocking.block(reporter.getId(), target.getId());
        mvc.perform(get(profilePath(target.getId())).header("Authorization", access(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("상대방(차단)"))
                .andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(get(profilePath(reporter.getId())).header("Authorization", access(target)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("신고자"))
                .andExpect(jsonPath("$.blocked").value(false));
        mvc.perform(get(profilePath(Long.MAX_VALUE)).header("Authorization", access(reporter))).andExpect(status().isNotFound());
    }

    @Test
    void profileAndReportEndpointsRequireAuthenticationAndCompletedOnboardingAndExposeNoReportList() throws Exception {
        var onboarding = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        for (String authorization : List.of("", "Bearer " + tokens.issueAccessToken(onboarding.getId(), true))) {
            int expected = authorization.isEmpty() ? 401 : 403;
            mvc.perform(get("/api/reports/reasons").param("targetType", "USER").header("Authorization", authorization))
                    .andExpect(status().is(expected));
            mvc.perform(get(profilePath(target.getId())).header("Authorization", authorization)).andExpect(status().is(expected));
            for (String path : List.of(userPath(target.getId()), roomPath(room.id()))) {
                mvc.perform(post(path).header("Authorization", authorization).contentType(APPLICATION_JSON)
                                .content("{\"reason\":\"ABUSE\",\"description\":\"내용\"}"))
                        .andExpect(status().is(expected));
            }
        }
        submit(userPath(onboarding.getId()), "{\"reason\":\"ABUSE\",\"description\":\"내용\"}", 404);
        for (String path : List.of("/api/reports", "/api/reports/1", "/api/admin/reports")) {
            mvc.perform(get(path).header("Authorization", access(reporter))).andExpect(status().isNotFound());
        }
    }

    private void submit(String path, String payload, int expected) throws Exception {
        mvc.perform(post(path).header("Authorization", access(reporter)).contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().is(expected));
    }
    private User user(String nickname) {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, nickname, "avatars/defaults/default1.jpg", "같이 직관해요"));
        return user;
    }
    private String access(User user) { return "Bearer " + tokens.issueAccessToken(user.getId(), false); }
    private String userPath(Long id) { return "/api/reports/users/" + id; }
    private String roomPath(Long id) { return "/api/reports/rooms/" + id; }
    private String profilePath(Long id) { return "/users/" + id + "/profile"; }
}
