package com.eCommerce.couponDomain.entity.enums;

/**
 * 쿠폰 캠페인 상태
 */
public enum CampaignStatus {
    ACTIVE,   // 진행 중 - 발급 가능
    INACTIVE, // 비활성 - 아직 시작 전이거나 일시 중단
    ENDED     // 종료 - 기간 만료 또는 수량 소진
}
