package com.jikchin.jikchin_app;

import com.jikchin.jikchin_app.chat.domain.ChatMessage;
import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import com.jikchin.jikchin_app.chat.domain.ChatThread;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.game.repository.TeamRepository;
import com.jikchin.jikchin_app.game.domain.LeagueCode;
import com.jikchin.jikchin_app.room.domain.Room;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import com.jikchin.jikchin_app.moderation.domain.ReportReason;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;
import com.jikchin.jikchin_app.moderation.domain.ReportStatus;
import com.jikchin.jikchin_app.moderation.dto.SubmitReportRequest;
import com.jikchin.jikchin_app.moderation.repository.ReportRepository;
import com.jikchin.jikchin_app.moderation.service.ReportService;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles({"test", "mysql-test"})
class FlywayMigrationTest {

    @Autowired Flyway flyway;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired EntityManager entityManager;
    @Autowired TeamRepository teams;
    @Autowired ChatParticipantRepository participants;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired LeaveRoomService leaveRooms;
    @Autowired RoomChatService chat;
    @Autowired ReportService reporting;
    @Autowired ReportRepository reports;

    @Test
    void applicationMigratesBeforeJpaValidatesTheSchema() {
        var current = flyway.info().current();

        assertThat(current).isNotNull();
        assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(flyway.info().applied())
                .filteredOn(migration -> migration.getVersion() != null && migration.getState() == MigrationState.SUCCESS)
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(teams.findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(LeagueCode.KBO)).hasSizeGreaterThanOrEqualTo(10);
        assertThat(teams.findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(LeagueCode.KLEAGUE)).hasSizeGreaterThanOrEqualTo(12);
        flyway.validate();
    }

    @Test
    @Transactional
    void repeatedMigrationPreservesPersistedUserAndProfile() {
        User user = users.saveAndFlush(User.createSocialUser("google", "migration-replay"));
        profiles.saveAndFlush(Profile.create(user, "직관친구", "avatars/defaults/default1.jpg", "같이 직관해요 🏟️"));
        entityManager.clear();

        assertThat(flyway.migrate().migrationsExecuted).isZero();

        User storedUser = users.findById(user.getId()).orElseThrow();
        Profile storedProfile = profiles.findById(user.getId()).orElseThrow();
        assertThat(storedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(storedProfile.getNickname()).isEqualTo("직관친구");
        assertThat(storedProfile.getBio()).isEqualTo("같이 직관해요 🏟️");
        assertThat(storedProfile.getCreatedAt()).isNotNull();
        assertThat(storedProfile.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void migrationEnforcesUniqueNicknames() {
        User first = users.saveAndFlush(User.createSocialUser("google", "nickname-first"));
        User second = users.saveAndFlush(User.createSocialUser("google", "nickname-second"));
        profiles.saveAndFlush(Profile.create(first, "same-nickname", "avatars/defaults/default1.jpg", null));

        assertThatThrownBy(() -> profiles.saveAndFlush(
                Profile.create(second, "same-nickname", "avatars/defaults/default1.jpg", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void migrationEnforcesUniqueChatMembership() {
        User user = users.saveAndFlush(User.createSocialUser("google", "unique-chat-membership"));
        ChatThread thread = ChatThread.createGroup(user, 4);
        entityManager.persist(thread);
        participants.saveAndFlush(ChatParticipant.join(thread, user));

        assertThatThrownBy(() -> participants.saveAndFlush(ChatParticipant.join(thread, user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void migratedTextColumnStoresLongMessagesAndEmoji() {
        User user = users.saveAndFlush(User.createSocialUser("google", "long-message"));
        ChatThread thread = ChatThread.createDirect(user);
        entityManager.persist(thread);
        String body = "같이 직관해요 🏟️ ".repeat(1000);
        ChatMessage message = ChatMessage.text(thread, user, body);
        entityManager.persist(message);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(ChatMessage.class, message.getId()).getBody()).isEqualTo(body);
    }

    @Test
    @Transactional
    void migratedSchemaSupportsChatVisibilityAndIdempotentSends() {
        var host = User.createSocialUser("google", "mysql-chat-host");
        host.activate();
        users.saveAndFlush(host);
        var guest = User.createSocialUser("google", "mysql-chat-guest");
        guest.activate();
        users.saveAndFlush(guest);
        var selectable = teams.findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(LeagueCode.KBO);
        var room = createRooms.create(host.getId(), new CreateRoomRequest("채팅", LocalDate.now(), "잠실",
                selectable.get(0).getId(), selectable.get(1).getId(), 3));
        var old = chat.send(host.getId(), room.id(), new SendMessageRequest("참여 전", java.util.UUID.randomUUID().toString()));
        joinRooms.join(guest.getId(), room.id());
        var request = new SendMessageRequest("이모지도 저장 🏟️", java.util.UUID.randomUUID().toString());
        var visible = chat.send(host.getId(), room.id(), request);
        assertThat(chat.send(host.getId(), room.id(), request).id()).isEqualTo(visible.id());
        entityManager.flush();
        entityManager.clear();
        assertThat(participants.findByChatThread_IdAndUser_Id(room.chatThreadId(), guest.getId()).orElseThrow()
                .getVisibleAfterMessageId()).isEqualTo(old.id());
        assertThat(chat.history(guest.getId(), room.id(), null, 50).messages())
                .extracting(message -> message.id()).containsExactly(visible.id());
        assertThat(entityManager.find(ChatMessage.class, visible.id()).getClientMessageId()).isEqualTo(request.clientMessageId());
    }

    @Test
    @Transactional
    void migratedSchemaSupportsHostTransferAndDeletingTheLastParticipantsRoom() {
        var host = User.createSocialUser("google", "mysql-room-host");
        host.activate();
        users.saveAndFlush(host);
        var guest = User.createSocialUser("google", "mysql-room-guest");
        guest.activate();
        users.saveAndFlush(guest);
        var selectable = teams.findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(LeagueCode.KBO);
        var room = createRooms.create(host.getId(), new CreateRoomRequest(
                "같이 직관해요", LocalDate.now(), "잠실", selectable.get(0).getId(), selectable.get(1).getId(), 2));
        joinRooms.join(guest.getId(), room.id());
        var message = ChatMessage.text(entityManager.find(ChatThread.class, room.chatThreadId()), host, "안녕하세요");
        entityManager.persist(message);

        leaveRooms.leave(host.getId(), room.id());
        entityManager.flush();
        entityManager.clear();
        var storedRoom = entityManager.find(Room.class, room.id());
        assertThat(storedRoom.getHost().getId()).isEqualTo(guest.getId());
        assertThat(storedRoom.getChatThread().getCreatedBy().getId()).isEqualTo(host.getId());
        assertThat(participants.countByChatThread_Id(room.chatThreadId())).isEqualTo(1);
        assertThat(entityManager.find(ChatMessage.class, message.getId())).isNotNull();

        leaveRooms.leave(guest.getId(), room.id());
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(Room.class, room.id())).isNull();
        assertThat(entityManager.find(ChatThread.class, room.chatThreadId())).isNull();
        assertThat(entityManager.find(ChatMessage.class, message.getId())).isNull();
        assertThat(participants.countByChatThread_Id(room.chatThreadId())).isZero();
    }

    @Test
    @Transactional
    void migratedSchemaKeepsReportsAndSnapshotsAfterRoomDeletion() {
        var host = users.saveAndFlush(User.createSocialUser("google", "mysql-report-host"));
        profiles.saveAndFlush(Profile.create(host, "신고 대상", "avatars/defaults/default1.jpg", null));
        var reporter = users.saveAndFlush(User.createSocialUser("google", "mysql-reporter"));
        profiles.saveAndFlush(Profile.create(reporter, "신고 접수자", "avatars/defaults/default1.jpg", null));
        var selectable = teams.findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(LeagueCode.KBO);
        var room = createRooms.create(host.getId(), new CreateRoomRequest("신고된 방 🏟️", LocalDate.now(), "잠실",
                selectable.get(0).getId(), selectable.get(1).getId(), 3));
        var userReport = reporting.submit(reporter.getId(), ReportTargetType.USER, host.getId(),
                new SubmitReportRequest(ReportReason.FRAUD, "신고 상세 내용 🏟️", null));
        String retryKey = java.util.UUID.randomUUID().toString();
        var request = new SubmitReportRequest(ReportReason.MISLEADING_ROOM_INFO, null, retryKey);
        var roomReport = reporting.submit(reporter.getId(), ReportTargetType.ROOM, room.id(), request);
        leaveRooms.leave(host.getId(), room.id());
        entityManager.flush();
        entityManager.clear();

        var stored = reports.findById(roomReport.report().id()).orElseThrow();
        assertThat(stored.getTargetSnapshot()).contains("신고된 방 🏟️");
        assertThat(stored.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(stored.getTargetId()).isEqualTo(room.id());
        assertThat(entityManager.find(Room.class, room.id())).isNull();
        assertThat(reports.findById(userReport.report().id()).orElseThrow().getDescription()).isEqualTo("신고 상세 내용 🏟️");
        assertThat(reporting.submit(reporter.getId(), ReportTargetType.ROOM, room.id(), request).report().id())
                .isEqualTo(roomReport.report().id());
    }
}
