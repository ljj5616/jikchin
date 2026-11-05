package com.jikchin.jikchin_app.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomChatTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired LeaveRoomService leaveRooms;
    @Autowired RoomChatService chat;
    private User host;
    private RoomResponse room;

    @BeforeEach
    void fixture() {
        jdbc.update("insert into league (code) values ('KBO')");
        int league = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values ('A','A','',?,true),('B','B','',?,true)", league, league);
        int home = jdbc.queryForObject("select id from team where name = 'A'", Integer.class);
        int away = jdbc.queryForObject("select id from team where name = 'B'", Integer.class);
        host = activeUser();
        room = createRooms.create(host.getId(), new CreateRoomRequest("채팅 테스트", LocalDate.now(), "잠실", home, away, 5));
    }

    @Test
    void storesTextAndUsesAuthenticatedSenderAndPreservesWhitespace() throws Exception {
        var request = request("  안녕하세요 🏟️\n같이 가요  ");
        var response = mvc.perform(post(path()).header("Authorization", access(host)).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.senderId").value(host.getId()))
                .andExpect(jsonPath("$.type").value("TEXT")).andExpect(jsonPath("$.body").value(request.body()))
                .andExpect(jsonPath("$.createdAt").isString()).andExpect(jsonPath("$.senderNickname").isString())
                .andExpect(jsonPath("$.senderAvatarUrl").value("https://assets.example.test/avatars/defaults/default1.jpg"))
                .andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get(path()).header("Authorization", access(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].id").value(id))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void joinAndRejoinHideEarlierMessagesAndOtherRooms() throws Exception {
        var guest = activeUser();
        chat.send(host.getId(), room.id(), request("입장 전"));
        joinRooms.join(guest.getId(), room.id());
        var visible = chat.send(host.getId(), room.id(), request("입장 후"));
        assertThat(chat.history(guest.getId(), room.id(), null, 50).messages()).extracting(m -> m.id()).containsExactly(visible.id());
        leaveRooms.leave(guest.getId(), room.id());
        chat.send(host.getId(), room.id(), request("퇴장 후"));
        mvc.perform(get(path()).header("Authorization", access(guest))).andExpect(status().isForbidden());
        joinRooms.join(guest.getId(), room.id());
        assertThat(chat.history(guest.getId(), room.id(), null, 50).messages()).isEmpty();
        var rejoined = chat.send(guest.getId(), room.id(), request("재입장 후"));
        assertThat(chat.history(guest.getId(), room.id(), null, 50).messages()).extracting(m -> m.id()).containsExactly(rejoined.id());
        assertThat(chat.history(host.getId(), room.id(), null, 50).messages()).hasSize(4);
    }

    @Test
    void historyUsesStableCursorAndChronologicalOrder() {
        var first = chat.send(host.getId(), room.id(), request("1"));
        var second = chat.send(host.getId(), room.id(), request("2"));
        var third = chat.send(host.getId(), room.id(), request("3"));
        var page = chat.history(host.getId(), room.id(), null, 2);
        assertThat(page.messages()).extracting(m -> m.id()).containsExactly(second.id(), third.id());
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextBeforeId()).isEqualTo(second.id());
        chat.send(host.getId(), room.id(), request("4"));
        var older = chat.history(host.getId(), room.id(), page.nextBeforeId(), 2);
        assertThat(older.messages()).extracting(m -> m.id()).containsExactly(first.id());
        assertThat(older.hasMore()).isFalse();
        assertThat(older.nextBeforeId()).isNull();
    }

    @Test
    void retriesAreIdempotentAndChangingContentWithTheSameIdIsRejected() throws Exception {
        var request = request("같은 메시지");
        var first = chat.send(host.getId(), room.id(), request);
        assertThat(chat.send(host.getId(), room.id(), request).id()).isEqualTo(first.id());
        mvc.perform(post(path()).header("Authorization", access(host)).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(new SendMessageRequest("다른 내용", request.clientMessageId()))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from chat_message", Long.class)).isEqualTo(1);
    }

    @Test
    void deniesUnauthenticatedOnboardingNonMembersAndUnknownRooms() throws Exception {
        var outsider = activeUser();
        var registering = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        for (String authorization : List.of("Bearer invalid", "")) {
            mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isUnauthorized());
            mvc.perform(post(path()).header("Authorization", authorization).contentType(APPLICATION_JSON)
                    .content(json.writeValueAsString(request("메시지")))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get(path()).header("Authorization", "Bearer " + tokens.issueAccessToken(registering.getId(), true)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path()).header("Authorization", access(outsider))).andExpect(status().isForbidden());
        mvc.perform(post(path()).header("Authorization", access(outsider)).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(request("메시지")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms/9223372036854775807/messages").header("Authorization", access(host)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesBodyRequestIdAndPagination() throws Exception {
        for (String body : List.of("", " \n\t", "\u2003", "a".repeat(2001))) {
            mvc.perform(post(path()).header("Authorization", access(host)).contentType(APPLICATION_JSON)
                    .content(json.writeValueAsString(request(body)))).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path()).header("Authorization", access(host)).contentType(APPLICATION_JSON)
                .content("{\"body\":\"hello\",\"clientMessageId\":\"invalid\"}")).andExpect(status().isBadRequest());
        for (String query : List.of("?size=0", "?size=101", "?beforeId=0", "?beforeId=bad")) {
            mvc.perform(get(path() + query).header("Authorization", access(host))).andExpect(status().isBadRequest());
        }
    }

    private String path() { return "/api/rooms/" + room.id() + "/messages"; }
    private String access(User user) { return "Bearer " + tokens.issueAccessToken(user.getId(), false); }
    private SendMessageRequest request(String body) { return new SendMessageRequest(body, UUID.randomUUID().toString()); }
    private User activeUser() {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, UUID.randomUUID().toString().substring(0, 20), "avatars/defaults/default1.jpg", null));
        return user;
    }
}
