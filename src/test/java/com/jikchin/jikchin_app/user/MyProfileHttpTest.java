package com.jikchin.jikchin_app.user;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:my-profile-http-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class MyProfileHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired TokenProviderPort tokens;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TransactionTemplate transactions;

    @Test
    void preservesTheProfileConflictStatusWhenTheServerRendersTheError() {
        Long userId = transactions.execute(status -> {
            member("다른회원");
            return member("내닉네임").getId();
        });
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokens.issueAccessToken(userId, false));
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = http.exchange("/users/me/profile", HttpMethod.PUT,
                new HttpEntity<>("{\"nickname\":\"다른회원\"}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(http.getForEntity("/users/me/profile", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private User member(String nickname) {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, nickname, "avatars/defaults/default1.jpg", null));
        return user;
    }
}
