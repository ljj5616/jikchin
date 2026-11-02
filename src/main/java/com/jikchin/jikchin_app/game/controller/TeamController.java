package com.jikchin.jikchin_app.game.controller;

import com.jikchin.jikchin_app.game.domain.LeagueCode;
import com.jikchin.jikchin_app.game.repository.TeamRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {
    private final TeamRepository teams;

    @Operation(summary = "리그별 팀 목록 조회", description = "league는 KBO 또는 KLEAGUE입니다. 온보딩 토큰도 사용할 수 있습니다.")
    @GetMapping
    @Transactional(readOnly = true)
    public List<TeamResponse> list(@RequestParam LeagueCode league) {
        return teams.findByLeague_CodeAndOnboardingSelectableTrueOrderByNameAsc(league).stream()
                .map(team -> new TeamResponse(team.getId(), team.getName(), team.getShortName(), team.getLogoUrl()))
                .toList();
    }

    public record TeamResponse(Integer id, String name, String shortName, String logoUrl) {}
}
