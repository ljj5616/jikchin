package com.jikchin.jikchin_app.application.port.out.team;

import com.jikchin.jikchin_app.domain.team.model.Team;

import java.util.Optional;

public interface KboTeamQueryPort {
    boolean existsById(Integer teamId);

    Optional<Team> findById(Integer id);
}
