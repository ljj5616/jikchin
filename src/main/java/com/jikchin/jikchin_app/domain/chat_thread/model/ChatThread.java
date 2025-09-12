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

    @Column(nullable = false)
    private boolean isDirect;

    @Setter(AccessLevel.PROTECTED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User createdBy;

    // 생성팩토리
    public static ChatThread create(User creator, boolean isDirect) {
        ChatThread thread = new ChatThread();
        thread.createdBy = creator;     // 세팅 주도권은 ChatThread가 가짐
        thread.isDirect = isDirect;
        // 필요하면 이 시점에 도메인 규칙 검사(예: direct면 추후 참가자 2명 강제)
        return thread;
    }
}