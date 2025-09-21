package com.jikchin.jikchin_app.adapter.out.persistence.profile;

import com.jikchin.jikchin_app.domain.profile.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileJpaRepository extends JpaRepository<Profile, Long> {
    boolean existsByNickname(String nickname);

    Optional<Profile> findByUserId(Long userId);
}
