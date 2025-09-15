package com.jikchin.jikchin_app.adapter.out.persistence.team;

import com.jikchin.jikchin_app.domain.team.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamJpaRepository extends JpaRepository<Team, Integer> {
}
