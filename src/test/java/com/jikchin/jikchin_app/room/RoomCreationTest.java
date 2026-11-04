package com.jikchin.jikchin_app.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomCreationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void createsMultipleRoomsAndRegistersHostInEachGroupChat() throws Exception {
        var user = User.createSocialUser("google", "room-host");
        user.activate();
        users.saveAndFlush(user);
        int[] ids = teams();
        String body = body(ids[0], ids[1], LocalDate.now().plusDays(2).toString(), 4, "  직관 가요  ");
        String access = tokens.issueAccessToken(user.getId(), false);
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        var onboarding = users.saveAndFlush(User.createSocialUser("google", "room-onboarding"));
        mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + tokens.issueAccessToken(onboarding.getId(), true))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("직관 가요"))
                    .andExpect(jsonPath("$.hostId").value(user.getId())).andExpect(jsonPath("$.participantCount").value(1))
                    .andExpect(jsonPath("$.capacity").value(4)).andExpect(jsonPath("$.chatThreadId").isNumber());
        }
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from chat_thread where thread_type = 'GROUP' and created_by_id = ? and capacity = 4", Integer.class, user.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from chat_participant where user_id = ?", Integer.class, user.getId())).isEqualTo(2);
        mvc.perform(get("/api/rooms/mine").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].participantCount").value(1));
        var other = User.createSocialUser("google", "other-room-user");
        other.activate();
        users.saveAndFlush(other);
        mvc.perform(get("/api/rooms/mine").header("Authorization", "Bearer " + tokens.issueAccessToken(other.getId(), false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsInvalidRequestsWithoutCreatingChat() throws Exception {
        var user = User.createSocialUser("google", "room-invalid");
        user.activate();
        users.saveAndFlush(user);
        int[] ids = teams();
        String access = tokens.issueAccessToken(user.getId(), false);
        for (String body : new String[]{
                body(ids[0], ids[0], LocalDate.now().toString(), 4, "title"),
                body(ids[0], ids[2], LocalDate.now().toString(), 4, "title"),
                body(ids[0], ids[1], LocalDate.now().minusDays(1).toString(), 4, "title"),
                body(ids[0], ids[1], LocalDate.now().toString(), 1, "title"),
                body(ids[0], ids[1], LocalDate.now().toString(), 4, "   ")}) {
            mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_thread", Integer.class)).isZero();
    }

    private int[] teams() {
        jdbc.update("insert into league (code) values ('KBO'), ('KLEAGUE')");
        int kbo = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        int football = jdbc.queryForObject("select id from league where code = 'KLEAGUE'", Integer.class);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values ('A', 'A', '', ?, true), ('B', 'B', '', ?, true), ('C', 'C', '', ?, true)", kbo, kbo, football);
        return new int[]{id("A"), id("B"), id("C")};
    }
    private int id(String name) { return jdbc.queryForObject("select id from team where name = ?", Integer.class, name); }
    private String body(int home, int away, String date, int capacity, String title) throws Exception {
        return json.writeValueAsString(Map.of("title", title, "gameDate", date, "stadium", "잠실", "homeTeamId", home, "awayTeamId", away, "capacity", capacity));
    }
}
