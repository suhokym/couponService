package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
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


}
