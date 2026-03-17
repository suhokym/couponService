package com.eCommerce.couponDomain.entity.enums;

/**
 * 사용자 쿠폰 상태
 */
public enum UserCouponStatus {
    ISSUED,   // 발급 완료 - 사용 가능한 상태
    USED,     // 사용 완료
    EXPIRED,  // 만료 - 유효 기간 초과
    RESERVED  // 주문에 사용 예약됨 - 결제 완료 전 임시 점유 상태
}
