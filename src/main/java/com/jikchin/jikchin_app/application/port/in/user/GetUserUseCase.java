package com.jikchin.jikchin_app.application.port.in.user;

import com.jikchin.jikchin_app.application.dto.user.UserResult;

public interface GetUserUseCase {
    UserResult getById(Long id);
}
