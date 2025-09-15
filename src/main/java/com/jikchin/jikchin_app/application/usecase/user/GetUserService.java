package com.jikchin.jikchin_app.application.usecase.user;

import com.jikchin.jikchin_app.application.dto.user.UserResult;
import com.jikchin.jikchin_app.application.port.in.user.GetUserUseCase;
import com.jikchin.jikchin_app.application.port.out.user.LoadUserPort;

public class GetUserService implements GetUserUseCase {

    private final LoadUserPort loadUserPort;

    public GetUserService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    public UserResult getById(Long id) {
        return new UserResult(id, "stub");
    }
}
