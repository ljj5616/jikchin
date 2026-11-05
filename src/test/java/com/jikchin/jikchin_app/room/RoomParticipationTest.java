package com.jikchin.jikchin_app.room;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomParticipationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TokenProviderPort tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void joinsMultipleRoomsAndIncludesThemInMyRooms() throws Exception {
        int[] teams = teams();
        var host = activeUser();
        var guest = activeUser();
        var first = createRoom(host, teams, 4, LocalDate.now());
        var second = createRoom(host, teams, 4, LocalDate.now().plusDays(1));

        for (var room : List.of(first, second)) {
            mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                            .header("Authorization", access(guest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(room.id()))
                    .andExpect(jsonPath("$.chatThreadId").value(room.chatThreadId()))
                    .andExpect(jsonPath("$.hostId").value(host.getId()))
                    .andExpect(jsonPath("$.participantCount").value(2));
            assertThat(jdbc.queryForObject(
                    "select count(*) from chat_participant where chat_thread_id = ? and user_id = ?",
                    Integer.class, room.chatThreadId(), guest.getId())).isEqualTo(1);
        }
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.id()))
                .andExpect(jsonPath("$[0].participantCount").value(2));
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/rooms/mine").header("Authorization", access(activeUser())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from chat_thread", Integer.class)).isEqualTo(2);
    }

    @Test
    void repeatedJoinAndHostJoinDoNotDuplicateMembershipEvenWhenFull() throws Exception {
        var host = activeUser();
        var guest = activeUser();
        var room = createRoom(host, teams(), 2, LocalDate.now());
        for (var user : List.of(guest, guest, host)) {
            mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                            .header("Authorization", access(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participantCount").value(2));
        }
        assertThat(participantCount(room)).isEqualTo(2);
    }

    @Test
    void rejectsMissingFullAndPastRoomsWithoutAddingParticipants() throws Exception {
        int[] teams = teams();
        var host = activeUser();
        var guest = activeUser();
        var outsider = activeUser();
        var full = createRoom(host, teams, 2, LocalDate.now());
        var past = createRoom(host, teams, 4, LocalDate.now());
        joinRooms.join(guest.getId(), full.id());
        jdbc.update("update room set game_date = ? where id = ?", LocalDate.now().minusDays(1), past.id());
        em.clear();

        mvc.perform(post("/api/rooms/{roomId}/participants", Long.MAX_VALUE)
                        .header("Authorization", access(outsider)))
                .andExpect(status().isNotFound());
        for (var room : List.of(full, past)) {
            mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                            .header("Authorization", access(outsider)))
                    .andExpect(status().isConflict());
        }
        assertThat(participantCount(full)).isEqualTo(2);
        assertThat(participantCount(past)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from chat_participant where user_id = ?",
                Integer.class, outsider.getId())).isZero();
    }

    @Test
    void roomListShowsUpcomingGamesInDateOrderWithCurrentParticipantCounts() throws Exception {
        int[] teams = teams();
        var host = activeUser();
        var guest = activeUser();
        var later = createRoom(host, teams, 4, LocalDate.now().plusDays(2));
        var today = createRoom(host, teams, 2, LocalDate.now());
        var past = createRoom(host, teams, 4, LocalDate.now());
        joinRooms.join(guest.getId(), today.id());
        jdbc.update("update room set game_date = ? where id = ?", LocalDate.now().minusDays(1), past.id());
        em.clear();

        mvc.perform(get("/api/rooms").header("Authorization", access(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(today.id()))
                .andExpect(jsonPath("$[0].participantCount").value(2))
                .andExpect(jsonPath("$[1].id").value(later.id()));
    }

    @Test
    void requiresAnActiveUsersAccessTokenForRoomListsAndJoining() throws Exception {
        var host = activeUser();
        var room = createRoom(host, teams(), 4, LocalDate.now());
        var onboarding = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        String onboardingAccess = "Bearer " + tokens.issueAccessToken(onboarding.getId(), true);

        for (String path : List.of("/api/rooms", "/api/rooms/mine")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", onboardingAccess)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/rooms/{roomId}/participants", room.id())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rooms/{roomId}/participants", room.id())
                        .header("Authorization", onboardingAccess))
                .andExpect(status().isForbidden());
        assertThat(participantCount(room)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentJoinsCannotExceedCapacity() throws Exception {
        assertConcurrentJoins(false);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetriesCreateOnlyOneMembership() throws Exception {
        assertConcurrentJoins(true);
    }

    private void assertConcurrentJoins(boolean sameUser) throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var fixture = transaction.execute(status -> {
            int[] teams = teams();
            var host = activeUser();
            var guests = new ArrayList<Long>();
            for (int i = 0; i < 6; i++) guests.add(activeUser().getId());
            return new ConcurrentFixture(createRoom(host, teams, 2, LocalDate.now()), teams, host.getId(), guests);
        });
        var executor = Executors.newFixedThreadPool(6);
        try {
            var start = new CyclicBarrier(6);
            List<Future<Integer>> results = new ArrayList<>();
            for (Long guestId : fixture.guestIds()) {
                Long userId = sameUser ? fixture.guestIds().get(0) : guestId;
                results.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        joinRooms.join(userId, fixture.room().id());
                        return 200;
                    } catch (ResponseStatusException exception) {
                        return exception.getStatusCode().value();
                    }
                }));
            }
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses).allMatch(code -> code == 200 || code == 409);
            assertThat(statuses.stream().filter(code -> code == 200).count()).isEqualTo(sameUser ? 6 : 1);
            assertThat(participantCount(fixture.room())).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> {
                jdbc.update("delete from chat_participant where chat_thread_id = ?", fixture.room().chatThreadId());
                jdbc.update("delete from room where id = ?", fixture.room().id());
                jdbc.update("delete from chat_thread where id = ?", fixture.room().chatThreadId());
                for (Long guestId : fixture.guestIds()) jdbc.update("delete from user where id = ?", guestId);
                jdbc.update("delete from user where id = ?", fixture.hostId());
                jdbc.update("delete from team where id in (?, ?)", fixture.teams()[0], fixture.teams()[1]);
                jdbc.update("delete from league where id = ?", fixture.teams()[2]);
            });
        }
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

    private RoomResponse createRoom(User host, int[] teams, int capacity, LocalDate date) {
        return createRooms.create(host.getId(), new CreateRoomRequest("직관 가요", date, "잠실", teams[0], teams[1], capacity));
    }

    private long participantCount(RoomResponse room) {
        return jdbc.queryForObject("select count(*) from chat_participant where chat_thread_id = ?", Long.class, room.chatThreadId());
    }

    private record ConcurrentFixture(RoomResponse room, int[] teams, Long hostId, List<Long> guestIds) {}
}
