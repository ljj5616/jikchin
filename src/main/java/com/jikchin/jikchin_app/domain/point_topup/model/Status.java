package com.jikchin.jikchin_app.domain.point_topup.model;

public enum Status {
    PENDING, // 생성 (결제 시도 전/중)
    SUCCESS, // 성공 (영수증 검증 및 정산 반영 완료)
    FAILED // 실패/취소/검증 불가
}
