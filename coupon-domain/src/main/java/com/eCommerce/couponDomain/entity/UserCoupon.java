package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.UserCouponStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 엔티티
 * - 실제 발급된 쿠폰을 나타내며, 사용자별로 고유한 쿠폰 코드를 가짐
 * - 주문 예약(RESERVED) → 결제 완료 시 사용(USED) 흐름으로 상태가 변경됨
 * - version 필드로 낙관적 락을 적용하여 동시 사용 중복을 방지
 */
@Entity
@Table(name = "user_coupon")
@Getter
@NoArgsConstructor
public class UserCoupon extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCouponId;

    @Column(nullable = false)
    private Long userId; // 쿠폰 소유 사용자 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CouponCampaign campaign; // 발급된 캠페인

    @Column(unique = true, nullable = false)
    private String couponCode; // 고유 쿠폰 코드

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status; // 쿠폰 상태 (ISSUED / USED / EXPIRED / RESERVED)

    private Long reservedOrderId; // 예약된 주문 ID (RESERVED 상태일 때 기록)

    private LocalDateTime reservedAt; // 주문 예약 시각

    private LocalDateTime usedAt; // 쿠폰 사용 완료 시각

    @Column(nullable = false)
    private LocalDateTime expiredAt; // 쿠폰 만료 일시

    @Version
    @Column(nullable = false)
    private int version = 0; // 낙관적 락 버전
}
