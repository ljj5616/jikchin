package com.jikchin.jikchin_app.room;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.auth.service.RefreshSessionService;
import com.jikchin.jikchin_app.chat.domain.ChatMessage;
import com.jikchin.jikchin_app.chat.domain.ChatThread;
import com.jikchin.jikchin_app.media.domain.MediaAsset;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomLeaveTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TokenProviderPort tokens;
    @Autowired RefreshSessionService refreshSessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired LeaveRoomService leaveRooms;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void leavingRemovesOnlyMyMembershipAndUpdatesRoomLists() throws Exception {
        int[] teams = teams();
        var host = activeUser();
        var guest = activeUser();
        var otherGuest = activeUser();
        var room = createRoom(host, teams, 4);
        var otherRoom = createRoom(host, teams, 4);
        joinRooms.join(guest.getId(), room.id());
        joinRooms.join(otherGuest.getId(), room.id());
        joinRooms.join(guest.getId(), otherRoom.id());
        var message = ChatMessage.text(em.find(ChatThread.class, room.chatThreadId()), guest, "같이 직관해요");
        em.persist(message);
        em.flush();

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(guest)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        em.flush();

        assertThat(membershipCount(room, guest.getId())).isZero();
        assertThat(membershipCount(room, host.getId())).isEqualTo(1);
        assertThat(membershipCount(room, otherGuest.getId())).isEqualTo(1);
        assertThat(membershipCount(otherRoom, guest.getId())).isEqualTo(1);
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherRoom.id()));
        mvc.perform(get("/api/rooms").header("Authorization", access(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(room.id()))
                .andExpect(jsonPath("$[0].participantCount").value(2));
        assertThat(jdbc.queryForObject("select count(*) from chat_thread", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select body from chat_message where id = ?", String.class, message.getId()))
                .isEqualTo("같이 직관해요");
    }

    @Test
    void repeatedLeaveAndNonParticipantLeaveAreIdempotent() throws Exception {
        var host = activeUser();
        var guest = activeUser();
        var outsider = activeUser();
        var room = createRoom(host, teams(), 2);
        joinRooms.join(guest.getId(), room.id());

        for (var user : List.of(guest, guest, outsider)) {
            mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                            .header("Authorization", access(user)))
                    .andExpect(status().isNoContent());
        }
        assertThat(participantCount(room)).isEqualTo(1);
        assertThat(membershipCount(room, host.getId())).isEqualTo(1);
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void leavingFreesCapacityAndAllowsRejoiningTheSameChat() throws Exception {
        var host = activeUser();
        var guest = activeUser();
        var newcomer = activeUser();
        var room = createRoom(host, teams(), 2);
        joinRooms.join(guest.getId(), room.id());

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(guest)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                        .header("Authorization", access(newcomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2));
        mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                        .header("Authorization", access(guest)))
                .andExpect(status().isConflict());
        leaveRooms.leave(newcomer.getId(), room.id());
        mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                        .header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatThreadId").value(room.chatThreadId()))
                .andExpect(jsonPath("$.participantCount").value(2));
        assertThat(membershipCount(room, guest.getId())).isEqualTo(1);
        assertThat(membershipCount(room, newcomer.getId())).isZero();
    }

    @Test
    void participantCanLeaveAfterTheGameDate() throws Exception {
        var host = activeUser();
        var guest = activeUser();
        var room = createRoom(host, teams(), 2);
        joinRooms.join(guest.getId(), room.id());
        jdbc.update("update room set game_date = ? where id = ?", LocalDate.now().minusDays(1), room.id());
        em.clear();

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(guest)))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(membershipCount(room, guest.getId())).isZero();
        assertThat(participantCount(room)).isEqualTo(1);
    }

    @Test
    void missingRoomReturnsNotFound() throws Exception {
        mvc.perform(delete("/api/rooms/{roomId}/participants/me", Long.MAX_VALUE)
                        .header("Authorization", access(activeUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void hostTransfersToEarliestRemainingParticipantAndSuccessorCanLeave() throws Exception {
        var host = activeUser();
        var first = activeUser();
        var second = activeUser();
        var third = activeUser();
        var room = createRoom(host, teams(), 4);
        joinRooms.join(first.getId(), room.id());
        joinRooms.join(second.getId(), room.id());
        joinRooms.join(third.getId(), room.id());
        leaveRooms.leave(first.getId(), room.id());

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(host)))
                .andExpect(status().isNoContent());
        assertHost(room, second, 2);
        assertThat(membershipCount(room, first.getId())).isZero();
        assertThat(membershipCount(room, host.getId())).isZero();
        assertThat(jdbc.queryForObject("select created_by_id from chat_thread where id = ?", Long.class, room.chatThreadId()))
                .isEqualTo(host.getId());
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Retrying the departed host's request must not transfer the new host again.
        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(host)))
                .andExpect(status().isNoContent());
        assertHost(room, second, 2);

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(second)))
                .andExpect(status().isNoContent());
        assertHost(room, third, 1);
    }

    @Test
    void hostSuccessionUsesJoinTimeThenMembershipId() throws Exception {
        var host = activeUser();
        var later = activeUser();
        var earlier = activeUser();
        var tied = activeUser();
        var room = createRoom(host, teams(), 4);
        joinRooms.join(later.getId(), room.id());
        joinRooms.join(earlier.getId(), room.id());
        joinRooms.join(tied.getId(), room.id());
        em.flush();
        var joinedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        jdbc.update("update chat_participant set joined_at = ? where chat_thread_id = ? and user_id in (?, ?)",
                joinedAt, room.chatThreadId(), earlier.getId(), tied.getId());
        jdbc.update("update chat_participant set joined_at = ? where chat_thread_id = ? and user_id = ?",
                joinedAt.plusMinutes(1), room.chatThreadId(), later.getId());
        em.clear();

        leaveRooms.leave(host.getId(), room.id());
        assertHost(room, earlier, 3);
        leaveRooms.leave(earlier.getId(), room.id());
        assertHost(room, tied, 2);
    }

    @Test
    void rejoinedParticipantGoesToEndOfHostSuccession() throws Exception {
        var host = activeUser();
        var first = activeUser();
        var second = activeUser();
        var room = createRoom(host, teams(), 3);
        joinRooms.join(first.getId(), room.id());
        joinRooms.join(second.getId(), room.id());
        leaveRooms.leave(first.getId(), room.id());
        joinRooms.join(first.getId(), room.id());

        leaveRooms.leave(host.getId(), room.id());
        assertHost(room, second, 2);
    }

    @Test
    void lastHostLeavingDeletesRoomChatAndMessageLinksWithoutAffectingOtherRooms() throws Exception {
        int[] teams = teams();
        var host = activeUser();
        var guest = activeUser();
        var room = createRoom(host, teams, 2);
        var otherRoom = createRoom(host, teams, 2);
        joinRooms.join(guest.getId(), room.id());
        var asset = MediaAsset.create(guest, "https://assets.example.test/chat/photo.png", "image/png", 100, 100);
        em.persist(asset);
        var message = ChatMessage.image(em.find(ChatThread.class, room.chatThreadId()), guest, "사진");
        message.addAsset(asset, 0);
        em.persist(message);
        var otherMessage = ChatMessage.image(em.find(ChatThread.class, otherRoom.chatThreadId()), host, "다른 방 사진");
        otherMessage.addAsset(asset, 0);
        em.persist(otherMessage);
        em.flush();
        leaveRooms.leave(guest.getId(), room.id());

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(host)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertThat(jdbc.queryForObject("select count(*) from room where id = ?", Integer.class, room.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_thread where id = ?", Integer.class, room.chatThreadId())).isZero();
        assertThat(participantCount(room)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_message where id = ?", Integer.class, message.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_message_asset where message_id = ?", Integer.class, message.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from media_asset where id = ?", Integer.class, asset.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from chat_message where id = ?", Integer.class, otherMessage.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from chat_message_asset where message_id = ?", Integer.class, otherMessage.getId())).isEqualTo(1);
        assertThat(participantCount(otherRoom)).isEqualTo(1);
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherRoom.id()));
        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", access(host)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                        .header("Authorization", access(guest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void leavingRequiresAnActiveUsersAccessToken() throws Exception {
        var host = activeUser();
        var guest = activeUser();
        var room = createRoom(host, teams(), 2);
        joinRooms.join(guest.getId(), room.id());
        var onboarding = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));

        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", "Bearer " + refreshSessions.issue(guest)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/rooms/{roomId}/participants/me", room.id())
                        .header("Authorization", "Bearer " + tokens.issueAccessToken(onboarding.getId(), true)))
                .andExpect(status().isForbidden());
        assertThat(participantCount(room)).isEqualTo(2);
        assertThat(membershipCount(room, guest.getId())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentLeavesAndJoinsKeepMembershipAndCapacityConsistent() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var fixture = transaction.execute(status -> {
            int[] teams = teams();
            var host = activeUser();
            var guest = activeUser();
            var room = createRoom(host, teams, 2);
            joinRooms.join(guest.getId(), room.id());
            var newcomers = List.of(activeUser().getId(), activeUser().getId(), activeUser().getId());
            return new ConcurrentFixture(room, teams, host.getId(), guest.getId(), newcomers);
        });
        var executor = Executors.newFixedThreadPool(6);
        try {
            var start = new CyclicBarrier(6);
            List<Future<Integer>> leaves = new ArrayList<>();
            List<Future<Integer>> joins = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                leaves.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    leaveRooms.leave(fixture.guestId(), fixture.room().id());
                    return 204;
                }));
            }
            for (Long userId : fixture.newcomerIds()) {
                joins.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        joinRooms.join(userId, fixture.room().id());
                        return 200;
                    } catch (ResponseStatusException exception) {
                        return exception.getStatusCode().value();
                    }
                }));
            }
            for (var result : leaves) assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo(204);
            var joinStatuses = new ArrayList<Integer>();
            for (var result : joins) joinStatuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(joinStatuses).allMatch(code -> code == 200 || code == 409);
            long joined = joinStatuses.stream().filter(code -> code == 200).count();
            assertThat(joined).isLessThanOrEqualTo(1);
            assertThat(participantCount(fixture.room())).isEqualTo(1 + joined);
            assertThat(membershipCount(fixture.room(), fixture.guestId())).isZero();
            assertThat(membershipCount(fixture.room(), fixture.hostId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> {
                jdbc.update("delete from chat_participant where chat_thread_id = ?", fixture.room().chatThreadId());
                jdbc.update("delete from room where id = ?", fixture.room().id());
                jdbc.update("delete from chat_thread where id = ?", fixture.room().chatThreadId());
                for (Long userId : fixture.newcomerIds()) jdbc.update("delete from user where id = ?", userId);
                jdbc.update("delete from user where id in (?, ?)", fixture.hostId(), fixture.guestId());
                jdbc.update("delete from team where id in (?, ?)", fixture.teams()[0], fixture.teams()[1]);
                jdbc.update("delete from league where id = ?", fixture.teams()[2]);
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentHostAndParticipantLeavesDeleteTheEmptyRoom() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var fixture = transaction.execute(status -> {
            int[] teams = teams();
            var host = activeUser();
            var guest = activeUser();
            var room = createRoom(host, teams, 2);
            joinRooms.join(guest.getId(), room.id());
            return new ConcurrentFixture(room, teams, host.getId(), guest.getId(), List.of());
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CyclicBarrier(2);
            List<Future<?>> results = new ArrayList<>();
            for (Long userId : List.of(fixture.hostId(), fixture.guestId())) {
                results.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    leaveRooms.leave(userId, fixture.room().id());
                    return null;
                }));
            }
            for (var result : results) result.get(20, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select count(*) from room where id = ?", Integer.class, fixture.room().id())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from chat_thread where id = ?", Integer.class, fixture.room().chatThreadId())).isZero();
            assertThat(participantCount(fixture.room())).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> {
                jdbc.update("delete from chat_participant where chat_thread_id = ?", fixture.room().chatThreadId());
                jdbc.update("delete from room where id = ?", fixture.room().id());
                jdbc.update("delete from chat_thread where id = ?", fixture.room().chatThreadId());
                jdbc.update("delete from user where id in (?, ?)", fixture.hostId(), fixture.guestId());
                jdbc.update("delete from team where id in (?, ?)", fixture.teams()[0], fixture.teams()[1]);
                jdbc.update("delete from league where id = ?", fixture.teams()[2]);
            });
        }
    }

    private void assertHost(RoomResponse room, User host, long participantCount) throws Exception {
        em.flush();
        assertThat(jdbc.queryForObject("select host_id from room where id = ?", Long.class, room.id())).isEqualTo(host.getId());
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(room.id()))
                .andExpect(jsonPath("$[0].hostId").value(host.getId()))
                .andExpect(jsonPath("$[0].participantCount").value(participantCount));
    }

    private User activeUser() {
        var user = User.createSocialUser("google", UUID.randomUUID().toString());
        user.activate();
        return users.saveAndFlush(user);
    }

    private String access(User user) {
        return "Bearer " + tokens.issueAccessToken(user.getId(), false);
    }

    private int[] teams() {
        jdbc.update("insert into league (code) values ('KBO')");
        int leagueId = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values ('A', 'A', '', ?, true), ('B', 'B', '', ?, true)", leagueId, leagueId);
        return new int[]{teamId("A"), teamId("B"), leagueId};
    }

    private int teamId(String name) {
        return jdbc.queryForObject("select id from team where name = ?", Integer.class, name);
    }

    private RoomResponse createRoom(User host, int[] teams, int capacity) {
        return createRooms.create(host.getId(), new CreateRoomRequest("직관 가요", LocalDate.now(), "잠실", teams[0], teams[1], capacity));
    }

    private long membershipCount(RoomResponse room, Long userId) {
        return jdbc.queryForObject("select count(*) from chat_participant where chat_thread_id = ? and user_id = ?",
                Long.class, room.chatThreadId(), userId);
    }

    private long participantCount(RoomResponse room) {
        return jdbc.queryForObject("select count(*) from chat_participant where chat_thread_id = ?", Long.class, room.chatThreadId());
    }

    private record ConcurrentFixture(RoomResponse room, int[] teams, Long hostId, Long guestId, List<Long> newcomerIds) {}
}
