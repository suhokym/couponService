package com.eCommerce.couponDomain.dto;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;

import java.time.LocalDateTime;

public record CouponIssueRequestDto(
        Long requestId,
        Long userId,
        Long campaignId,
        IssueRequestStatus status,
        String failReason,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CouponIssueRequestDto from(CouponIssueRequest entity) {
        return new CouponIssueRequestDto(
                entity.getRequestId(),
                entity.getUserId(),
                entity.getCampaign().getCouponId(),
                entity.getStatus(),
                entity.getFailReason(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
