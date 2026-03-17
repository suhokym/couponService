package com.eCommerce.couponDomain.dto;

import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;

import java.time.LocalDateTime;

public record CouponCampaignDto(
        Long CouponId,
        String name,
        int totalQuantity,
        CampaignStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CouponCampaignDto from(CouponCampaign entity) {
        return new CouponCampaignDto(
                entity.getCouponId(),
                entity.getName(),
                entity.getTotalQuantity(),
                entity.getStatus(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
