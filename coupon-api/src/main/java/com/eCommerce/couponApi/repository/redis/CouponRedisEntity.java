package com.eCommerce.couponApi.repository.redis;

import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;

import java.io.Serializable;
import java.time.LocalDateTime;

public record CouponRedisEntity (
        Long id,
        CampaignType campaignType,
        Integer totalQuantity,
        LocalDateTime start_at,
        LocalDateTime end_at,
        CampaignStatus campaignStatus
)implements Serializable {
    public CouponRedisEntity(CouponCampaign coupon){
        this(
                coupon.getCouponId(),
                coupon.getType(),
                coupon.getTotalQuantity(),
                coupon.getStartAt(),
                coupon.getEndAt(),
                coupon.getStatus()
        );
    }
}
