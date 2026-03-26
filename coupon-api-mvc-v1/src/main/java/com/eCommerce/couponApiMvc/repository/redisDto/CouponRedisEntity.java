package com.eCommerce.couponApiMvc.repository.redisDto;

import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;

import java.io.Serializable;
import java.time.LocalDateTime;

import static com.eCommerce.couponDomain.exception.ErrorCode.FAIL_COUPON_ISSUE_REQUEST;
import static com.eCommerce.couponDomain.exception.ErrorCode.INVALID_COUPON_ISSUE_DATE;

public record CouponRedisEntity(
        Long id,
        CampaignType campaignType,
        Integer totalQuantity,
        LocalDateTime start_at,
        LocalDateTime end_at,
        CampaignStatus campaignStatus
) implements Serializable {

    public CouponRedisEntity(CouponCampaign coupon) {
        this(
                coupon.getCouponId(),
                coupon.getType(),
                coupon.getTotalQuantity(),
                coupon.getStartAt(),
                coupon.getEndAt(),
                coupon.getStatus()
        );
    }

    public void availableIssueableCoupon() {
        LocalDateTime now = LocalDateTime.now();
        if (!start_at.isBefore(now) && end_at.isAfter(now)) {
            throw new CouponIssueException(
                    INVALID_COUPON_ISSUE_DATE,
                    "쿠폰 기한이 유효하지 않습니다 now: %s, start_at: %s, end_at: %s".formatted(now, start_at, end_at));
        }
        if (campaignStatus == CampaignStatus.ENDED) {
            throw new CouponIssueException(FAIL_COUPON_ISSUE_REQUEST, "쿠폰발급이 종료되었습니다.");
        }
        if (campaignStatus == CampaignStatus.INACTIVE) {
            throw new CouponIssueException(FAIL_COUPON_ISSUE_REQUEST, "쿠폰이 현재 유효하지 않습니다.");
        }
    }
}
