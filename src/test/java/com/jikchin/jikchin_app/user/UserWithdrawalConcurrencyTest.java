package com.jikchin.jikchin_app.user;

import com.jikchin.jikchin_app.auth.service.RefreshSessionService;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.service.WithdrawUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:withdrawal-concurrency;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserWithdrawalConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired WithdrawUserService withdrawal;
    @Autowired RefreshSessionService sessions;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired BlockService blocking;
    @Autowired JdbcTemplate jdbc;

    @Test
    void simultaneousWithdrawalOfBothHostsDeletesSharedRoomsWithoutDeadlocking() throws Exception {
        var first = member();
        var second = member();
        int[] teams = teams();
        var one = room(first, teams);
        var two = room(second, teams);
        joinRooms.join(second.getId(), one.id());
        joinRooms.join(first.getId(), two.id());

        assertThat(race(List.of(() -> withdrawal.withdraw(first.getId()), () -> withdrawal.withdraw(second.getId()))))
                .containsOnly(200);

        assertDeleted(first);
        assertDeleted(second);
        for (var room : List.of(one, two)) {
            assertThat(jdbc.queryForObject("select count(*) from room where id = ?", Long.class, room.id())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from chat_thread where id = ?", Long.class, room.chatThreadId())).isZero();
        }
    }

    @Test
    void concurrentCreationJoinBlockAndRefreshCannotSurviveWithdrawal() throws Exception {
        var user = member();
        var other = member();
        int[] teams = teams();
        var room = room(other, teams);
        String refresh = sessions.issue(user);

        var outcomes = race(List.of(() -> withdrawal.withdraw(user.getId()),
                () -> createRooms.create(user.getId(), request(teams)),
                () -> joinRooms.join(user.getId(), room.id()),
                () -> blocking.block(other.getId(), user.getId()),
                () -> sessions.refresh(refresh),
                () -> sessions.issue(user)));
        assertThat(outcomes).allMatch(code -> List.of(200, 401, 403, 404, 409).contains(code));
        // A room committed between the initial scan and user lock produces a safe 409.
        // Retry only after all other mutations finish.
        withdrawal.withdraw(user.getId());

        assertDeleted(user);
        assertThat(jdbc.queryForObject("select count(*) from block where blocker_id = ? or blocked_id = ?",
                Long.class, user.getId(), user.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from room where host_id = ?", Long.class, user.getId())).isZero();
    }

    private List<Integer> race(List<Runnable> actions) throws Exception {
        var executor = Executors.newFixedThreadPool(actions.size());
        var start = new CyclicBarrier(actions.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (var action : actions) futures.add(executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    action.run();
                    return 200;
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            }));
            List<Integer> outcomes = new ArrayList<>();
            for (var future : futures) outcomes.add(future.get(20, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertDeleted(User user) {
        assertThat(jdbc.queryForObject("select status from user where id = ?", String.class, user.getId())).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from chat_participant where user_id = ?", Long.class, user.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from auth_refresh_session where user_id = ?", Long.class, user.getId())).isZero();
    }

    private User member() {
        var user = User.createSocialUser("google", UUID.randomUUID().toString());
        user.activate();
        return users.saveAndFlush(user);
    }

    private int[] teams() {
        jdbc.update("insert into league(code) select 'KBO' where not exists (select 1 from league where code = 'KBO')");
        int league = jdbc.queryForObject("select id from league where code = 'KBO'", Integer.class);
        String prefix = UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("insert into team (name, short_name, logo_url, league_id, onboarding_selectable) values (?, 'A', '', ?, true), (?, 'B', '', ?, true)", prefix + "A", league, prefix + "B", league);
        return new int[]{jdbc.queryForObject("select id from team where name = ?", Integer.class, prefix + "A"),
                jdbc.queryForObject("select id from team where name = ?", Integer.class, prefix + "B")};
    }

    private RoomResponse room(User host, int[] teams) { return createRooms.create(host.getId(), request(teams)); }

    private CreateRoomRequest request(int[] teams) {
        return new CreateRoomRequest("동시성 검증", LocalDate.now(), "잠실", teams[0], teams[1], 5);
    }
}
