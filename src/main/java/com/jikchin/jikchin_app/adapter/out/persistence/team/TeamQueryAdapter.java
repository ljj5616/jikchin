package com.jikchin.jikchin_app.adapter.out.persistence.team;

import com.jikchin.jikchin_app.application.port.out.team.KLeagueTeamQueryPort;
import com.jikchin.jikchin_app.application.port.out.team.KboTeamQueryPort;
import com.jikchin.jikchin_app.domain.team.model.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TeamQueryAdapter implements KboTeamQueryPort, KLeagueTeamQueryPort {

    private final TeamJpaRepository repository;


    @Override
    public boolean existsById(Integer teamId) {
        return repository.existsById(teamId);
    }

    @Override
    public Optional<Team> findById(Integer id) {
        return repository.findById(id);
    }
}
