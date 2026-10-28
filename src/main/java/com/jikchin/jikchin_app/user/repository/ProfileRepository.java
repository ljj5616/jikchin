package com.jikchin.jikchin_app.user.repository;

import com.jikchin.jikchin_app.user.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    boolean existsByNickname(String nickname);

    Optional<Profile> findByUserId(Long userId);
}
