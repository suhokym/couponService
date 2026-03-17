package com.eCommerce.couponDomain.entity.enums;

/**
 * 쿠폰 캠페인 발급 방식
 */
public enum CampaignType {
    FIRST_COME, // 선착순 발급 - 수량 제한 있음, 소진 시 마감
    CODE,       // 코드 입력 발급 - 코드를 알고 있는 사용자만 발급 가능
    OPEN,       // 버튼 클릭 발급 - 수량 무제한, 누구나 발급 가능 (totalQuantity = null)
    MANUAL      // 관리자 직접 발급 - 특정 사용자에게 관리자가 수동 발급
}
