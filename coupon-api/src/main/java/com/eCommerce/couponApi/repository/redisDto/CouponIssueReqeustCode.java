package com.eCommerce.couponApi.repository.redisDto;

import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import reactor.core.publisher.Mono;


public enum CouponIssueReqeustCode {
    FAIL(0),
    SUCCESS(1),
    DUPLICATE_COUPON_ISSUE(2),
    INVALID_COUPON_ISSUE_QUANTITY(3);

    CouponIssueReqeustCode(int code) {

    }

    public static CouponIssueReqeustCode findCode(String code) {
        int codeVal = Integer.parseInt(code);
        if (codeVal == 0) {
            return CouponIssueReqeustCode.FAIL;
        }
        if (codeVal == 1) {
            return CouponIssueReqeustCode.SUCCESS;
        }
        if (codeVal == 2) {
            return CouponIssueReqeustCode.DUPLICATE_COUPON_ISSUE;
        }
        if (codeVal == 3) {
            return CouponIssueReqeustCode.INVALID_COUPON_ISSUE_QUANTITY;
        }
        throw new IllegalArgumentException("존재하지 않는 코드입니다. %s".formatted(codeVal));

    }

    public static void checkRequestResult(CouponIssueReqeustCode code) {

        if (code == FAIL) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST,
                    "발급에 실패했습니다.");
        }if (code == INVALID_COUPON_ISSUE_QUANTITY) {
                throw new CouponIssueException(ErrorCode.INVALID_COUPON_ISSUE_QUANTITY,
                        "발급 가능한 수량을 초과합니다.");
            }
            if (code == DUPLICATE_COUPON_ISSUE) {
                throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE,
                        "이미 발급 요청된 쿠폰입니다.");
            }
        }


}
