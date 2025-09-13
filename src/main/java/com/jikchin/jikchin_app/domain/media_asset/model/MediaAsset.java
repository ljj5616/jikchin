package com.jikchin.jikchin_app.domain.media_asset.model;

import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_media_owner", columnList = "user_id")
})
public class MediaAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static MediaAsset create(User owner, String url, String mime, int w, int h) {
        MediaAsset mediaAsset = new MediaAsset();
        mediaAsset.user = owner;
        mediaAsset.url = url;
        mediaAsset.mimeType = mime;
        mediaAsset.width = w; mediaAsset.height = h;

        return mediaAsset;
    }
}
