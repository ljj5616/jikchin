package com.jikchin.jikchin_app.domain.point_account.model;

import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_account")
public class PointAccount extends BaseEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Version
    private Long version;

    @Column(nullable = false)
    private Integer balance = 0;

    public static PointAccount open(User user) {
        PointAccount pointAccount = new PointAccount();
        pointAccount.user = user;
        pointAccount.userId = user.getId();
        pointAccount.balance = 0;
        return pointAccount;
    }

    /** 내부 사용: 원장 반영 직후 호출 (음수 방지 검증은 서비스에서) */
    public void applyDelta(int delta) { this.balance += delta; }
}
