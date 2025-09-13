package com.jikchin.jikchin_app.domain.chat_message_asset.model;

import com.jikchin.jikchin_app.domain.chat_message.model.ChatMessage;
import com.jikchin.jikchin_app.domain.media_asset.model.MediaAsset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_msg_asset", columnNames = {"message_id", "asset_id"}),
                @UniqueConstraint(name = "uq_msg_position", columnNames = {"message_id", "position"})
        },
        indexes = {
                @Index(name = "idx_msg_asset_message", columnList = "message_id"),
                @Index(name = "idx_msg_asset_asset", columnList = "asset_id")
        }
)
public class ChatMessageAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private MediaAsset asset;

    @Column(nullable = false)
    private Integer position; // 0,1,2... (배열 순서용)

    public static ChatMessageAsset of(ChatMessage msg, MediaAsset asset, int pos) {
        ChatMessageAsset link = new ChatMessageAsset();
        link.message = msg;
        link.asset = asset;
        link.position = pos;

        return link;
    }
}
