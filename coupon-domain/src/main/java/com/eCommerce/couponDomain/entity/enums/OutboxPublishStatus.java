package com.eCommerce.couponDomain.entity.enums;

/**
 * Outbox 이벤트 Kafka 발행 상태
 */
public enum OutboxPublishStatus {
    PENDING,   // 발행 대기 중
    PUBLISHED, // Kafka 발행 완료
    FAILED     // 발행 실패 (재시도 대상)
}
