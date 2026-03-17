package com.eCommerce.couponDomain.dto;

import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;

import java.time.LocalDateTime;

public record CouponEventLogDto(
        Long eventId,
        Long requestId,
        String eventType,
        String payload,
        EventProcessingStatus processingStatus,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static CouponEventLogDto from(CouponEventLog entity) {
        return new CouponEventLogDto(
                entity.getEventId(),
                entity.getRequest().getRequestId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getProcessingStatus(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }
}
