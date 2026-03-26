package com.eCommerce.couponApiMvc.repository.redisDto;

import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;

public enum CouponIssueRequestCode {
    FAIL(0),
    SUCCESS(1),
    DUPLICATE_COUPON_ISSUE(2),
    INVALID_COUPON_ISSUE_QUANTITY(3);

    CouponIssueRequestCode(int code) {
    }

    public static CouponIssueRequestCode findCode(String code) {
        int codeVal = Integer.parseInt(code);
        if (codeVal == 0) return FAIL;
        if (codeVal == 1) return SUCCESS;
        if (codeVal == 2) return DUPLICATE_COUPON_ISSUE;
        if (codeVal == 3) return INVALID_COUPON_ISSUE_QUANTITY;
        throw new IllegalArgumentException("존재하지 않는 코드입니다. %s".formatted(codeVal));
    }

    public static void checkRequestResult(CouponIssueRequestCode code) {
        if (code == FAIL) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST, "발급에 실패했습니다.");
        }
        if (code == INVALID_COUPON_ISSUE_QUANTITY) {
            throw new CouponIssueException(ErrorCode.INVALID_COUPON_ISSUE_QUANTITY, "발급 가능한 수량을 초과합니다.");
        }
        if (code == DUPLICATE_COUPON_ISSUE) {
            throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급 요청된 쿠폰입니다.");
        }
    }
}
