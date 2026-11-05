package com.jikchin.jikchin_app.user;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles(value = {"test", "mysql-test"}, inheritProfiles = false)
class UserWithdrawalMysqlTest extends UserWithdrawalTest {
}
