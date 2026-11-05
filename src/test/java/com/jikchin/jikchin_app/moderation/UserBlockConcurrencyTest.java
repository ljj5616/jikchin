package com.jikchin.jikchin_app.moderation;

import com.jikchin.jikchin_app.moderation.repository.BlockRepository;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-block-concurrency;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserBlockConcurrencyTest {
    @Autowired BlockService blocking;
    @Autowired BlockRepository blocks;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;

    private User alice;
    private User bob;
    private RoomResponse room;

    @BeforeEach
    void fixture() {
        jdbc.update("delete from block");
        jdbc.update("delete from chat_participant");
        jdbc.update("delete from room");
        jdbc.update("delete from chat_thread");
        jdbc.update("delete from user");
        jdbc.update("delete from team");
        jdbc.update("delete from league");
        alice = user();
        bob = user();
        jdbc.update("insert into league(code) values ('KBO')");
        int league = jdbc.queryForObject("select id from league", Integer.class);
        jdbc.update("insert into team(name,short_name,logo_url,league_id,onboarding_selectable) values ('A','A','',?,true),('B','B','',?,true)", league, league);
        int home = jdbc.queryForObject("select id from team where name='A'", Integer.class);
        int away = jdbc.queryForObject("select id from team where name='B'", Integer.class);
        room = createRooms.create(alice.getId(), new CreateRoomRequest("동시성 확인", LocalDate.now(), "잠실", home, away, 5));
    }

    @Test
    void concurrentDuplicateBlocksAndUnblocksAreIdempotent() throws Exception {
        concurrently(() -> blocking.block(alice.getId(), bob.getId()));
        assertThat(blocks.count()).isEqualTo(1);
        concurrently(() -> blocking.unblock(alice.getId(), bob.getId()));
        assertThat(blocks.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void joinWaitsForAnUncommittedBlockAndIsRejectedAfterItCommits(boolean guestBlocksCreator) throws Exception {
        var blockWritten = new CountDownLatch(1);
        var releaseBlock = new CountDownLatch(1);
        var joinStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var block = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                blocking.block(guestBlocksCreator ? bob.getId() : alice.getId(), guestBlocksCreator ? alice.getId() : bob.getId());
                blockWritten.countDown();
                await(releaseBlock);
            }));
            assertThat(blockWritten.await(5, TimeUnit.SECONDS)).isTrue();
            var join = executor.submit(() -> {
                joinStarted.countDown();
                try {
                    joinRooms.join(bob.getId(), room.id());
                    return 200;
                } catch (ResponseStatusException e) {
                    return e.getStatusCode().value();
                }
            });
            assertThat(joinStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> join.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseBlock.countDown();
            block.get(10, TimeUnit.SECONDS);
            assertThat(join.get(10, TimeUnit.SECONDS)).isEqualTo(403);
            assertThat(jdbc.queryForObject("select count(*) from chat_participant", Integer.class)).isEqualTo(1);
        } finally {
            releaseBlock.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void simultaneousOppositeBlocksSucceedWithoutDeadlock() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        try {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                blocking.block(alice.getId(), bob.getId());
                return null;
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                blocking.block(bob.getId(), alice.getId());
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(blocks.count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void concurrently(Runnable action) throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        var start = new CyclicBarrier(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                action.run();
                return null;
            }));
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private User user() {
        var user = User.createSocialUser("google", UUID.randomUUID().toString());
        user.activate();
        return users.saveAndFlush(user);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("차단 트랜잭션 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
