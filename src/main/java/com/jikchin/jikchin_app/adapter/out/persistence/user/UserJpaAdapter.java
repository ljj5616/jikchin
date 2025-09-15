package com.jikchin.jikchin_app.adapter.out.persistence.user;

import com.jikchin.jikchin_app.application.port.out.user.UserRepositoryPort;
import com.jikchin.jikchin_app.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class UserJpaAdapter implements UserRepositoryPort {
    private final UserJpaRepository repository;

    @Override
    public Optional<User> findByProviderAndProviderId(String provider, String providerId) {
        return repository.findByProviderAndProviderId(provider, providerId);
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public User save(User user) {
        return repository.save(user);
    }
}
