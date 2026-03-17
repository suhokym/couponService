package com.eCommerce.couponDomain.entity.enums;

/**
 * 쿠폰 발급 요청 처리 상태
 */
public enum IssueRequestStatus {
    REQUESTED,        // 발급 요청 접수됨 (Kafka 발행 전)
    PROCESSING,       // Consumer가 처리 중
    ISSUED,           // 발급 완료

    FAILED_BUSINESS,  // 비즈니스 오류로 실패 (중복 발급, 수량 초과 등) - 재시도 불필요
    FAILED_RETRYABLE, // 일시적 오류로 실패 (DB 타임아웃 등) - 재시도 가능
    FAILED_FATAL,     // 복구 불가 오류로 최종 실패

    RETRYING          // 재시도 진행 중
}
