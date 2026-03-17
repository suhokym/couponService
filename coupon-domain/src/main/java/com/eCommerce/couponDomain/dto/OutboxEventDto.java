package com.eCommerce.couponDomain.dto;

import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;

import java.time.LocalDateTime;

public record OutboxEventDto(
        Long outboxId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload,
        OutboxPublishStatus publishStatus,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OutboxEventDto from(OutboxEvent entity) {
        return new OutboxEventDto(
                entity.getOutboxId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getPublishStatus(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
