package com.jikchin.jikchin_app.config;

import com.jikchin.jikchin_app.application.port.in.user.GetUserUseCase;
import com.jikchin.jikchin_app.application.port.out.user.LoadUserPort;
import com.jikchin.jikchin_app.application.usecase.user.GetUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    GetUserUseCase getUserUseCase(LoadUserPort loadUserPort) {
        return new GetUserService(loadUserPort);
    }
}
