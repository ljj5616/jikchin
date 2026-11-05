package com.jikchin.jikchin_app.moderation;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.moderation.repository.ReportRepository;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.reports.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:reports-disabled;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired TokenProviderPort tokens;
    @Autowired ReportRepository reports;

    @Test
    void disabledReportingRemainsDiscoverableButRejectsSubmissionWithoutAffectingProfiles() throws Exception {
        var user = users.saveAndFlush(User.createSocialUser("google", UUID.randomUUID().toString()));
        profiles.saveAndFlush(Profile.create(user, "설정확인", "avatars/defaults/default1.jpg", null));
        String access = "Bearer " + tokens.issueAccessToken(user.getId(), false);
        for (String type : List.of("USER", "ROOM")) {
            mvc.perform(get("/api/reports/reasons").param("targetType", type).header("Authorization", access))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.reasons").isArray());
        }
        for (String path : List.of("/api/reports/users/1", "/api/reports/rooms/1")) {
            mvc.perform(post(path).header("Authorization", access).contentType(APPLICATION_JSON)
                            .content("{\"reason\":\"OTHER\",\"description\":\"신고 내용\"}"))
                    .andExpect(status().isServiceUnavailable());
        }
        mvc.perform(get("/users/{id}/profile", user.getId()).header("Authorization", access)).andExpect(status().isOk());
        assertThat(reports.count()).isZero();
    }
}
