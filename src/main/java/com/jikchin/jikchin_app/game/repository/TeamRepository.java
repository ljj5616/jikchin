package com.jikchin.jikchin_app.game.repository;

import com.jikchin.jikchin_app.game.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Integer> {
}
