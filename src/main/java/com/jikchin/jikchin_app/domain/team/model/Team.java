package com.jikchin.jikchin_app.domain.team.model;

import com.jikchin.jikchin_app.domain.league.model.League;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "team", uniqueConstraints = @UniqueConstraint(columnNames = {"league_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, length = 10)
    private String shortName;

    @Column(nullable = false, length = 200)
    private String logoUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id")
    private League league;
}
