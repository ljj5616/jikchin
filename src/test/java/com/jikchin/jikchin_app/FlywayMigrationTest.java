package com.jikchin.jikchin_app;

import com.jikchin.jikchin_app.chat.domain.ChatMessage;
import com.jikchin.jikchin_app.chat.domain.ChatThread;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
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

    @Test
    void applicationMigratesBeforeJpaValidatesTheSchema() {
        var current = flyway.info().current();

        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("1");
        assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
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
}
