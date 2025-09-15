package com.jikchin.jikchin_app.adapter.out.persistence.user;

import com.jikchin.jikchin_app.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
