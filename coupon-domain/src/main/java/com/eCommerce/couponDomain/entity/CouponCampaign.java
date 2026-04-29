package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 캠페인 엔티티
 * - 쿠폰 발급의 기준이 되는 캠페인 정보를 관리
 * - 발급 방식(type)에 따라 선착순 / 코드 입력 / 버튼 클릭 / 관리자 발급으로 구분
 */
@Entity
@Table(name = "coupon_campaign")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponCampaign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long couponId;

    @Column(nullable = false)
    private String name; // 캠페인명

    @Column(nullable = true)
    private Integer totalQuantity; // 총 발급 가능 수량 (null = 수량 무제한, OPEN 타입에서 사용)

    @Column(nullable = true)
    private Integer IssuedQuantity; // 현재 발급 수량 (null = 수량 무제한, OPEN 타입에서 사용)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type; // 발급 방식 (FIRST_COME / CODE / OPEN / MANUAL)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status; // 캠페인 진행 상태 (ACTIVE / INACTIVE / ENDED)

    @Column(nullable = false)
    private LocalDateTime startAt; // 캠페인 시작 일시

    @Column(nullable = false)
    private LocalDateTime endAt; // 캠페인 종료 일시

    public void updateStatus(CampaignStatus status) {
        this.status = status;
    }

    public void updateIssuedQuantity() {
        this.IssuedQuantity = this.IssuedQuantity + 1;
    }

    // ⚠️ NOTE: 벌크 처리 후 실제 DB 발급 수로 동기화 — INCREMENT 누락 방지용
    public void syncIssuedQuantity(int count) {
        this.IssuedQuantity = count;
    }

    // ⚠️ NOTE: DB 저장 직전 2차 방어 검증 — Redis 캐시 불일치나 캐시 미스 상황에서도
    //          비정상 발급을 차단하기 위해 CouponRedisEntity.availableIssueableCoupon()과
    //          동일한 3가지 조건을 DB 엔티티 레벨에서 재검증함
    public void validateIssuable() {
        // 1. 캠페인 활성 상태 체크
        if (this.status != CampaignStatus.ACTIVE) {
            throw new CouponIssueException(
                ErrorCode.FAIL_COUPON_ISSUE_REQUEST,
                "발급 가능한 상태가 아닌 캠페인입니다. status=%s couponId=%d".formatted(this.status, this.couponId)
            );
        }
        // 2. 발급 기간 체크
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(this.startAt) || now.isAfter(this.endAt)) {
            throw new CouponIssueException(
                ErrorCode.INVALID_COUPON_ISSUE_DATE,
                "발급 기간이 유효하지 않습니다. couponId=%d".formatted(this.couponId)
            );
        }
        // 3. 수량 체크 (null = 무제한, OPEN 타입)
        if (this.totalQuantity != null && this.IssuedQuantity >= this.totalQuantity) {
            throw new CouponIssueException(
                ErrorCode.INVALID_COUPON_ISSUE_QUANTITY,
                "발급 가능한 수량을 초과했습니다. couponId=%d".formatted(this.couponId)
            );
        }
    }
}
