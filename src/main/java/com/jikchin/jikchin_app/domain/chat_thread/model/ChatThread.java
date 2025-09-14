package com.jikchin.jikchin_app.domain.chat_thread.model;

import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatThread extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ThreadType threadType;

    @Column(nullable = false)
    private Integer capacity;

    @Setter(AccessLevel.PROTECTED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User createdBy;

    public static ChatThread createDirect(User creator) {
        ChatThread thread = new ChatThread();
        thread.threadType = ThreadType.DIRECT;
        thread.createdBy = creator;
        thread.capacity = 2;

        return thread;
    }

    public static ChatThread createGroup(User creator, Integer capacity) {
        ChatThread thread = new ChatThread();
        thread.threadType = ThreadType.GROUP;
        thread.createdBy = creator;
        thread.capacity = capacity;

        return thread;
    }
}