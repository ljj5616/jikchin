package com.jikchin.jikchin_app.application.usecase.user;

import com.jikchin.jikchin_app.application.port.in.user.UserResult;
import com.jikchin.jikchin_app.application.port.in.user.GetUserUseCase;
import com.jikchin.jikchin_app.application.port.out.user.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetUserService implements GetUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    public UserResult getById(Long id) {
        return new UserResult(id, "stub");
    }
}
