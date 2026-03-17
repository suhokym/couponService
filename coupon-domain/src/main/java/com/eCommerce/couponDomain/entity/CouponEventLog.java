package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 쿠폰 이벤트 처리 로그 엔티티
 * - Consumer가 Kafka 메시지를 처리한 결과를 기록
 * - 성공/실패/재시도 이력을 추적하여 장애 분석 및 재처리에 활용
 */
@Entity
@Table(name = "coupon_event_log")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CouponEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private CouponIssueRequest request; // 연관된 발급 요청

    @Column(nullable = false)
    private String eventType; // 처리한 이벤트 타입 (예: COUPON_ISSUE_REQUESTED)

    @Column(columnDefinition = "json")
    private String payload; // 이벤트 페이로드 (JSON)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventProcessingStatus processingStatus; // 처리 결과 상태

    private String errorMessage; // 실패 시 오류 메시지

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 로그 기록 시각
}
