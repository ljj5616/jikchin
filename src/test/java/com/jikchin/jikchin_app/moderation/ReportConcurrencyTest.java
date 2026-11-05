package com.jikchin.jikchin_app.moderation;

import com.jikchin.jikchin_app.moderation.domain.ReportReason;
import com.jikchin.jikchin_app.moderation.domain.ReportTargetType;
import com.jikchin.jikchin_app.moderation.dto.SubmitReportRequest;
import com.jikchin.jikchin_app.moderation.repository.ReportRepository;
import com.jikchin.jikchin_app.moderation.service.ReportService;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:report-concurrency;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired ReportService reporting;
    @Autowired ReportRepository reports;

    @Test
    void simultaneousRetriesCreateOnePendingReportAndReturnTheSameReceipt() throws Exception {
        var reporter = activeUser();
        var target = activeUser();
        var request = new SubmitReportRequest(ReportReason.SPAM, "반복 광고", UUID.randomUUID().toString());
        var start = new CyclicBarrier(4);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var results = new ArrayList<Future<ReportService.Submission>>();
            for (int i = 0; i < 4; i++) results.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return reporting.submit(reporter.getId(), ReportTargetType.USER, target.getId(), request);
            }));
            var responses = new ArrayList<ReportService.Submission>();
            for (var result : results) responses.add(result.get(10, TimeUnit.SECONDS));
            assertThat(responses.stream().map(response -> response.report().id()).distinct()).hasSize(1);
            assertThat(responses.stream().filter(ReportService.Submission::created)).hasSize(1);
            assertThat(reports.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private User activeUser() {
        var user = User.createSocialUser("google", UUID.randomUUID().toString());
        user.activate();
        return users.saveAndFlush(user);
    }
}
