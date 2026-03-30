package com.eCommerce.couponDomain.exception;

public enum ErrorCode {
    INVALID_COUPON_ISSUE_QUANTITY("쿠폰 발급 가능한 수량을 초과합니다"),
    INVALID_COUPON_ISSUE_DATE("쿠폰 발급 기한이 유효하지 않습니다."),
    COUPON_NOT_EXIST("존재하지 않는 쿠폰입니다."),
    DUPLICATED_COUPON_ISSUE("이미 발급된 쿠폰입니다."),
    COUPON_ISSUE_REQUEST_NOT_FOUND("존재하지 않는 발급 요청입니다."),
    FAIL_COUPON_ISSUE_REQUEST("쿠폰 발급 요청에 실패 했습니다."),
    DUPLICATED_COUPON_ISSUE_EVENT("이미 발급된 이벤트입니다."),
    FAIL_COUPON_EVENT_LOG_ISSUE("이벤트 로그 적재에 실패했습니다."),
    FAIL_OUTBOX_SAVE("아웃박스 적재에 실패했습니다."),
    OUTBOX_NOT_EXIST("존재하지 않는 아웃박스입니다.");
    public final String message;

    ErrorCode(String message) {
        this.message = message;
    }
}
