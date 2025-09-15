package com.jikchin.jikchin_app.application.port.out;

import com.jikchin.jikchin_app.domain.user.model.User;

import java.util.Optional;

public interface LoadUserPort {
    Optional<User> loadById(Long id);
}
