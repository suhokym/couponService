package com.eCommerce.couponConsumer.config;

import com.eCommerce.couponConsumer.service.KafkaProducingService;
import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final CouponIssueOutboxService outboxService;
    private final KafkaProducingService kafkaProducingService;
    private final ObjectMapper objectMapper;

    //아웃박스 성공, 실패시 로직은 로그만 찍기
    @Scheduled(fixedDelay = 5000)
    public void publishPendingOutbox() {
        List<OutboxEvent> pendingEvents = outboxService.findPendingOutbox();

        for (OutboxEvent event : pendingEvents) {
            try {
                // ⚠️ NOTE: payload JSON → CouponIssueEventDto 역직렬화
                CouponIssueEventDto dto = objectMapper.readValue(event.getPayload(), CouponIssueEventDto.class);
                kafkaProducingService.cosumeIssueComplete(dto);
                outboxService.markPublished(event.getOutboxId());
                log.info("[Outbox] 발행 성공 - aggregateId: {}", event.getAggregateId());
            } catch (Exception e) {
                outboxService.markFailed(event.getOutboxId());
                log.error("[Outbox] 발행 실패 - aggregateId: {}", event.getAggregateId());
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void retryFailedOutbox() {
        List<OutboxEvent> failedEvents = outboxService.findRetryableFailedOutbox();

        for (OutboxEvent event : failedEvents) {
            try {
                CouponIssueEventDto dto = objectMapper.readValue(event.getPayload(), CouponIssueEventDto.class);
                kafkaProducingService.cosumeIssueComplete(dto);
                outboxService.markPublished(event.getOutboxId());
            } catch (Exception e) {
                outboxService.markFailed(event.getOutboxId());
                //알림로직 작성
                log.error("[Outbox] 재시도 실패 - aggregateId: {}, retryCount: {}",
                        event.getAggregateId(), event.getRetryCount());
            }
        }
    }
}