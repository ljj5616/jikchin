package com.jikchin.jikchin_app.domain.point_topup.model;

import com.jikchin.jikchin_app.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_topup",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_topup_idempotency", columnNames = {"idempotencyKey"}),
                @UniqueConstraint(name = "uq_topup_purchase_token", columnNames = {"purchaseToken"}),
                @UniqueConstraint(name = "uq_topup_external_txid", columnNames = {"externalTransactionId"})
        },
        indexes = {
                @Index(name = "idx_topup_user_requested", columnList = "user_id, requestedAt"),
                @Index(name = "idx_topup_status_requested", columnList = "status, requestedAt"),
                @Index(name = "idx_topup_provider", columnList = "provider")
        })
public class PointTopup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer amount;

    /** 결제 제공자 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** 멱등키(클라이언트 생성 or 서버 생성) */
    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ---------- Play Billing 관련(해당 시 세팅) ----------
    /** Google Purchase Token */
    @Column(length = 200)
    private String purchaseToken;

    /** Play 콘솔 패키지명/상품ID(영수증 검증에 도움) */
    @Column(length = 200)
    private String packageName;
    @Column(length = 100)
    private String productId;

    /** 소비(acknowledge/consume) 완료 시각 */
    private LocalDateTime playAcknowledgeAt;

    // ---------- 대체결제(카카오/네이버 등) 관련 ----------
    /** 외부 PG 거래 ID(TID)/주문번호 */
    @Column(length = 200)
    private String externalTransactionId;

    /** PG별 영수증/서명 원문(JSON 등) 필요 시 저장 */
    @Lob
    private String receiptPayload;

    // ---------- 공통 타임라인 ----------
    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    // ---------- 팩토리 & 상태 전이 ----------

    public static PointTopup pendingPlay(User user, int amount, String idempotencyKey,
                                         String packageName, String productId) {
        PointTopup t = basePending(user, amount, Provider.PLAY, idempotencyKey);
        t.packageName = packageName;
        t.productId = productId;
        return t;
    }

    public static PointTopup pendingAlt(User user, int amount, String idempotencyKey,
                                        Provider altProvider) {
        PointTopup t = basePending(user, amount, altProvider, idempotencyKey);
        return t;
    }

    private static PointTopup basePending(User user, int amount, Provider provider, String idemKey) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        PointTopup t = new PointTopup();
        t.user = user;
        t.amount = amount;
        t.provider = provider;
        t.status = Status.PENDING;
        t.idempotencyKey = idemKey;
        t.requestedAt = LocalDateTime.now();
        return t;
    }

    /** Play Billing 결제 성공 처리(서버 영수증 검증 후) */
    public void markPlaySuccess(String purchaseToken, LocalDateTime acknowledgeAt, String receiptJson) {
        this.purchaseToken = purchaseToken;
        this.playAcknowledgeAt = acknowledgeAt;
        this.receiptPayload = receiptJson;
        this.status = Status.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    /** 대체결제(카카오/네이버 등) 성공 처리(서명 검증/거래보고 후) */
    public void markAltSuccess(String externalTid, String receiptJson) {
        this.externalTransactionId = externalTid;
        this.receiptPayload = receiptJson;
        this.status = Status.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String receiptOrReason) {
        this.receiptPayload = receiptOrReason;
        this.status = Status.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
