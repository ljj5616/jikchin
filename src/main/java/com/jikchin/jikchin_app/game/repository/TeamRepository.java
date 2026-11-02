package com.jikchin.jikchin_app.game.repository;

import com.jikchin.jikchin_app.game.domain.Team;
import com.jikchin.jikchin_app.game.domain.LeagueCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Integer> {
    List<Team> findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(LeagueCode code);
}
