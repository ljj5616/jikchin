package com.jikchin.jikchin_app.domain.point_ledger.model;

import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_ledger",
        indexes = {
                @Index(name = "idx_ledger_user_created", columnList = "user_id, createdAt"),
                @Index(name = "idx_ledger_reftype_refid", columnList = "refType, refId")
        })
public class PointLedger extends BaseEntity { // 거래 내역, 디버깅/리플레이 용도

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Reason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RefType refType;

    @Column
    private Long refId;

    @Column(nullable = false)
    private Integer balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static PointLedger of(User user, int delta, Reason reason, RefType refType, Long refId, int balanceAfter) {
        PointLedger pointLedger = new PointLedger();
        pointLedger.user = user;
        pointLedger.delta = delta;
        pointLedger.reason = reason;
        pointLedger.refType = refType;
        pointLedger.refId = refId;
        pointLedger.balanceAfter = balanceAfter;

        return pointLedger;
    }
}
