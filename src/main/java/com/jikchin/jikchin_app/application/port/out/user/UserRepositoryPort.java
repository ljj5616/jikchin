package com.jikchin.jikchin_app.application.port.out.user;

import com.jikchin.jikchin_app.domain.user.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    Optional<User> findById(Long id);
    User save(User user);
}
