package com.jikchin.jikchin_app.application.port.in.user;

public interface GetUserUseCase {
    UserResult getById(Long id);
}
