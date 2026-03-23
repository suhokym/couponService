package com.eCommerce.couponDomain.dto;

import com.eCommerce.couponDomain.entity.UserCoupon;
import com.eCommerce.couponDomain.entity.enums.UserCouponStatus;

import java.time.LocalDateTime;

public record UserCouponDto(
        Long userCouponId,
        String userId,
        Long campaignId,
        String couponCode,
        UserCouponStatus status,
        Long reservedOrderId,
        LocalDateTime reservedAt,
        LocalDateTime usedAt,
        LocalDateTime expiredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserCouponDto from(UserCoupon entity) {
        return new UserCouponDto(
                entity.getUserCouponId(),
                entity.getUserId(),
                entity.getCampaign().getCouponId(),
                entity.getCouponCode(),
                entity.getStatus(),
                entity.getReservedOrderId(),
                entity.getReservedAt(),
                entity.getUsedAt(),
                entity.getExpiredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
