package com.jikchin.jikchin_app.room.domain;

import com.jikchin.jikchin_app.chat.domain.ChatThread;
import com.jikchin.jikchin_app.game.domain.Team;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import com.jikchin.jikchin_app.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false)
    private LocalDate gameDate;
    @Column(nullable = false, length = 100)
    private String stadium;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team homeTeam;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team awayTeam;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_thread_id", nullable = false, unique = true)
    private ChatThread chatThread;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    public static Room create(String title, LocalDate gameDate, String stadium, Team homeTeam, Team awayTeam, ChatThread thread) {
        Room room = new Room();
        room.title = title;
        room.gameDate = gameDate;
        room.stadium = stadium;
        room.homeTeam = homeTeam;
        room.awayTeam = awayTeam;
        room.chatThread = thread;
        room.host = thread.getCreatedBy();
        return room;
    }

    public void transferHostTo(User participant) {
        this.host = participant;
    }
}
