package com.jikchin.jikchin_app.user.repository;

import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    // A scalar query reads current DB state even if an earlier request/filter loaded the entity.
    @Query("select user.status from User user where user.id = :userId")
    Optional<UserStatus> findStatusById(@Param("userId") Long userId);
}
