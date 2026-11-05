package com.jikchin.jikchin_app.moderation;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import com.jikchin.jikchin_app.moderation.repository.BlockRepository;
import com.jikchin.jikchin_app.moderation.service.BlockService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserBlockTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired BlockRepository blocks;
    @Autowired BlockService blocking;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired LeaveRoomService leaveRooms;
    @Autowired RoomChatService chat;

    private User alice;
    private User bob;
    private User carol;
    private int home;
    private int away;

    @BeforeEach
    void fixture() {
        alice = user("앨리스");
        bob = user("밥");
        carol = user("캐롤");
        jdbc.update("insert into league (code) values ('KBO')");
        int league = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        jdbc.update("insert into team(name,short_name,logo_url,league_id,onboarding_selectable) values ('A','A','',?,true),('B','B','',?,true)", league, league);
        home = jdbc.queryForObject("select id from team where name = 'A'", Integer.class);
        away = jdbc.queryForObject("select id from team where name = 'B'", Integer.class);
    }

    @Test
    void blockAndUnblockAreIdempotentAndListOnlyTheAuthenticatedUsersBlocks() throws Exception {
        String path = blockPath(bob);
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(path).header("Authorization", access(alice))).andExpect(status().isNoContent());
        }
        assertThat(blocks.count()).isEqualTo(1);
        mvc.perform(get("/users/me/blocks").header("Authorization", access(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(bob.getId()))
                .andExpect(jsonPath("$[0].nickname").value("밥(차단)"))
                .andExpect(jsonPath("$[0].avatarUrl").value("https://assets.example.test/avatars/defaults/default1.jpg"))
                .andExpect(jsonPath("$[0].blockedAt").isString())
                .andExpect(jsonPath("$[0].providerId").doesNotExist());
        for (User viewer : List.of(bob, carol)) {
            mvc.perform(get("/users/me/blocks").header("Authorization", access(viewer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
        // A third party cannot remove Alice's block by deleting its own relation.
        mvc.perform(delete(path).header("Authorization", access(carol))).andExpect(status().isNoContent());
        assertThat(blocks.existsByBlocker_IdAndBlocked_Id(alice.getId(), bob.getId())).isTrue();
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(path).header("Authorization", access(alice))).andExpect(status().isNoContent());
        }
        mvc.perform(get("/users/me/blocks").header("Authorization", access(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blockListUsesLatestProfileAndOrdersNewestBlocksFirst() throws Exception {
        blocking.block(alice.getId(), bob.getId());
        blocking.block(alice.getId(), carol.getId());
        jdbc.update("update profile set nickname = ?, avatar_key = ? where user_id = ?",
                "새닉네임", "avatars/users/" + bob.getId() + "/photo #1.jpg", bob.getId());
        em.clear();
        mvc.perform(get("/users/me/blocks").header("Authorization", access(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(carol.getId()))
                .andExpect(jsonPath("$[1].nickname").value("새닉네임(차단)"))
                .andExpect(jsonPath("$[1].avatarUrl").value("https://assets.example.test/avatars/users/" + bob.getId() + "/photo%20%231.jpg"));
    }

    @Test
    void rejectsSelfInvalidAndMissingTargetsAndRequiresCompletedProfile() throws Exception {
        var onboarding = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        for (String target : List.of(alice.getId().toString(), "0", "-1", "not-a-number")) {
            mvc.perform(put("/users/me/blocks/" + target).header("Authorization", access(alice)))
                    .andExpect(status().isBadRequest());
        }
        for (Long target : List.of(Long.MAX_VALUE, onboarding.getId())) {
            mvc.perform(put("/users/me/blocks/" + target).header("Authorization", access(alice)))
                    .andExpect(status().isNotFound());
        }
        String onboardingAccess = "Bearer " + tokens.issueAccessToken(onboarding.getId(), true);
        for (var request : List.of(get("/users/me/blocks"), put(blockPath(bob)), delete(blockPath(bob)))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        for (var request : List.of(get("/users/me/blocks"), put(blockPath(bob)), delete(blockPath(bob)))) {
            mvc.perform(request.header("Authorization", onboardingAccess)).andExpect(status().isForbidden());
        }
        assertThat(blocks.count()).isZero();
    }

    @Test
    void oneWayBlockPreventsBothUsersFromJoiningEachOthersExistingAndFutureRooms() throws Exception {
        var aliceRoom = room(alice);
        var bobRoom = room(bob);
        blocking.block(alice.getId(), bob.getId());
        assertCannotJoin(bob, aliceRoom);
        assertCannotJoin(alice, bobRoom);
        assertCannotJoin(bob, room(alice));
        assertCannotJoin(alice, room(bob));
        mvc.perform(get("/api/rooms/{id}", bobRoom.id()).header("Authorization", access(alice)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from chat_participant", Integer.class)).isEqualTo(4);
        blocking.unblock(alice.getId(), bob.getId());
        mvc.perform(post(joinPath(aliceRoom)).header("Authorization", access(bob))).andExpect(status().isOk());
        mvc.perform(post(joinPath(bobRoom)).header("Authorization", access(alice))).andExpect(status().isOk());
    }

    @Test
    void removingOneSideOfAMutualBlockKeepsTheOtherSideAndRoomRestriction() throws Exception {
        var aliceRoom = room(alice);
        var bobRoom = room(bob);
        blocking.block(alice.getId(), bob.getId());
        blocking.block(bob.getId(), alice.getId());
        blocking.unblock(alice.getId(), bob.getId());
        assertCannotJoin(bob, aliceRoom);
        assertCannotJoin(alice, bobRoom);
        assertThat(blocks.existsByBlocker_IdAndBlocked_Id(bob.getId(), alice.getId())).isTrue();
        blocking.unblock(bob.getId(), alice.getId());
        mvc.perform(post(joinPath(aliceRoom)).header("Authorization", access(bob))).andExpect(status().isOk());
    }

    @Test
    void blockingKeepsExistingMembershipAndChatButPreventsReentryAfterLeaving() throws Exception {
        var room = room(alice);
        joinRooms.join(bob.getId(), room.id());
        blocking.block(alice.getId(), bob.getId());
        mvc.perform(post(joinPath(room)).header("Authorization", access(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantCount").value(2));
        var message = chat.send(bob.getId(), room.id(), request("기존 참여자는 대화를 계속합니다"));
        assertThat(chat.history(alice.getId(), room.id(), null, 50).messages()).hasSize(1);
        assertThat(message.senderNickname()).isEqualTo("밥");
        leaveRooms.leave(bob.getId(), room.id());
        assertCannotJoin(bob, room);
    }

    @Test
    void thirdPartyRoomsAllowBothUsersAndNicknameDecorationIsViewerSpecificAndReversible() throws Exception {
        var room = room(carol);
        joinRooms.join(alice.getId(), room.id());
        blocking.block(alice.getId(), bob.getId());
        mvc.perform(post(joinPath(room)).header("Authorization", access(bob))).andExpect(status().isOk());
        chat.send(bob.getId(), room.id(), request("메시지 본문은 유지"));
        for (User viewer : List.of(alice, bob, carol)) {
            String expected = viewer == alice ? "밥(차단)" : "밥";
            mvc.perform(get("/api/rooms/{id}/participants", room.id()).header("Authorization", access(viewer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[2].nickname").value(expected));
            mvc.perform(get("/api/rooms/{id}/messages", room.id()).header("Authorization", access(viewer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].senderNickname").value(expected))
                    .andExpect(jsonPath("$.messages[0].body").value("메시지 본문은 유지"));
        }
        assertThat(profiles.findById(bob.getId()).orElseThrow().getNickname()).isEqualTo("밥");
        blocking.unblock(alice.getId(), bob.getId());
        mvc.perform(get("/api/rooms/{id}/participants", room.id()).header("Authorization", access(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[2].nickname").value("밥"));
        assertThat(chat.history(alice.getId(), room.id(), null, 50).messages().get(0).senderNickname()).isEqualTo("밥");
    }

    @Test
    void creatorRemainsTheEntryRestrictionBasisAfterHostTransferAndNewHostIsDisplayedCorrectly() throws Exception {
        var room = room(alice);
        joinRooms.join(carol.getId(), room.id());
        blocking.block(alice.getId(), bob.getId());
        leaveRooms.leave(alice.getId(), room.id());
        assertCannotJoin(bob, room);
        mvc.perform(get("/api/rooms/{id}/participants", room.id()).header("Authorization", access(carol)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(carol.getId()))
                .andExpect(jsonPath("$[0].host").value(true));
        blocking.unblock(alice.getId(), bob.getId());
        blocking.block(carol.getId(), bob.getId());
        mvc.perform(post(joinPath(room)).header("Authorization", access(bob))).andExpect(status().isOk());
    }

    private User user(String nickname) {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, nickname, "avatars/defaults/default1.jpg", null));
        return user;
    }

    private RoomResponse room(User creator) {
        return createRooms.create(creator.getId(), new CreateRoomRequest("직관", LocalDate.now(), "잠실", home, away, 5));
    }

    private String access(User user) { return "Bearer " + tokens.issueAccessToken(user.getId(), false); }
    private String blockPath(User target) { return "/users/me/blocks/" + target.getId(); }
    private String joinPath(RoomResponse room) { return "/api/rooms/" + room.id() + "/participants"; }
    private SendMessageRequest request(String body) { return new SendMessageRequest(body, UUID.randomUUID().toString()); }

    private void assertCannotJoin(User entrant, RoomResponse room) throws Exception {
        mvc.perform(post(joinPath(room)).header("Authorization", access(entrant))).andExpect(status().isForbidden());
    }
}
