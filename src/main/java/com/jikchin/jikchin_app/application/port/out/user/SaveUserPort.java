package com.jikchin.jikchin_app.application.port.out.user;

import com.jikchin.jikchin_app.domain.user.model.User;

public interface SaveUserPort {
    User save(User user);
}
