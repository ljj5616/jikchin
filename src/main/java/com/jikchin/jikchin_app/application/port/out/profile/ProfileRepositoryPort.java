package com.jikchin.jikchin_app.application.port.out.profile;

import com.jikchin.jikchin_app.domain.profile.model.Profile;

import java.util.Optional;

public interface ProfileRepositoryPort {
    boolean existsByNickname(String nickname);

    Optional<Profile> findByUserId(Long userId);

    Profile save(Profile profile);

}
