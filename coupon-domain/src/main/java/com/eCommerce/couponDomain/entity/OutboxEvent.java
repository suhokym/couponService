package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbox 이벤트 엔티티
 * - Transactional Outbox 패턴을 구현하기 위한 테이블
 * - DB 트랜잭션과 함께 이벤트를 저장하고, 별도 스케줄러가 Kafka로 발행
 * - 발행 실패 시 retryCount를 통해 재시도 관리
 */
@Entity
@Table(name = "outbox_event")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long outboxId;

    @Column(nullable = false)
    private String aggregateType; // 이벤트 발생 도메인 (예: CouponIssueRequest)

    @Column(nullable = false, unique = true)
    private Long aggregateId; // 도메인 엔티티의 PK

    @Column(nullable = false)
    private String eventType; // 이벤트 타입 (예: COUPON_ISSUED)

    @Column(nullable = false, columnDefinition = "json")
    private String payload; // 이벤트 데이터 (JSON)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxPublishStatus publishStatus = OutboxPublishStatus.PENDING; // Kafka 발행 상태

    @Column(nullable = false)
    private int retryCount = 0; // Kafka 발행 재시도 횟수
}
