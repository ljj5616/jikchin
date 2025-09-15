package com.jikchin.jikchin_app.adapter.out.persistence.profile;

import com.jikchin.jikchin_app.application.port.out.profile.ProfileRepositoryPort;
import com.jikchin.jikchin_app.domain.profile.model.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProfileJpaAdapter implements ProfileRepositoryPort {

    private final ProfileJpaRepository repository;

    @Override
    public boolean existsByNickname(String nickname) {
        return repository.existsByNickname(nickname);
    }

    @Override
    public Optional<Profile> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public Profile save(Profile profile) {
        return repository.save(profile);
    }
}
