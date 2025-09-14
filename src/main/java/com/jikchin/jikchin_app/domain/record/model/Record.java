package com.jikchin.jikchin_app.domain.record.model;

import com.jikchin.jikchin_app.domain.game.model.Game;
import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Record extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordVisibility visibility;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

     @ManyToOne(fetch = FetchType.LAZY, optional = false)
     @JoinColumn(name = "game_id", nullable = false)
     private Game game;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RecordImage> images = new ArrayList<>();

    public static Record create(User user, Game game, String title, String content, RecordVisibility visibility) {
        Record record = new Record();
        record.user = user;
        record.game = game;
        record.title = title;
        record.content = content;
        record.visibility = visibility;

        return record;
    }

    public void addImage(RecordImage image) {
        images.add(image);
        image.setRecord(this);
    }

    public void removeImage(RecordImage image) {
        images.remove(image);
        image.setRecord(null);
    }

    public void softDelete() { this.isDeleted = true; }
    public boolean isPublic() { return this.visibility == RecordVisibility.PUBLIC; }
}
