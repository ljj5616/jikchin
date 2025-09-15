package com.jikchin.jikchin_app.adapter.out.persistence.user;

import com.jikchin.jikchin_app.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
}
