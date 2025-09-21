package com.jikchin.jikchin_app.domain.record.model;

import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private Record record;

    public static RecordImage of(String imageUrl) {
        RecordImage image = new RecordImage();
        image.imageUrl = imageUrl;

        return image;
    }

    void setRecord(Record record) {
        this.record = record;
    }
}
