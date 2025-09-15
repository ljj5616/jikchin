package com.jikchin.jikchin_app.adapter.out.persistence.user;

import com.jikchin.jikchin_app.application.port.out.user.LoadUserPort;
import com.jikchin.jikchin_app.application.port.out.user.SaveUserPort;
import com.jikchin.jikchin_app.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class UserJpaAdapter implements LoadUserPort, SaveUserPort {
    private final UserJpaRepository repository;

    @Override
    public Optional<User> loadById(Long id) {
        return repository.findById(id);
    }

    @Override
    public User save(User user) {
        return repository.save(user);
    }
}
