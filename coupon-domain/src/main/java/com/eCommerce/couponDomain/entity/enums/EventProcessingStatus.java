package com.eCommerce.couponDomain.entity.enums;

/**
 * 쿠폰 이벤트 처리 결과 상태
 */
public enum EventProcessingStatus {
    SUCCESS, // 이벤트 처리 성공
    FAILED,  // 이벤트 처리 실패
    RETRYING // 재시도 중
}
