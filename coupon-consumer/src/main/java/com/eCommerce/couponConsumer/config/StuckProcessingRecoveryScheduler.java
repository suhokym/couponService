package com.eCommerce.couponConsumer.config;

import com.eCommerce.couponConsumer.service.KafkaProducingService;
import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckProcessingRecoveryScheduler {

    private final CouponIssueService couponIssueService;
    private final KafkaProducingService kafkaProducingService;

    // PROCESSING 상태로 멈춰있다고 판단하는 기준 시간 (분)
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    // ⚠️ NOTE: OutboxScheduler와 동일한 패턴 (fixedDelay)
    //          60초마다 실행 — 너무 잦으면 DB 부하, 너무 드물면 복구 지연
    @Scheduled(fixedDelay = 60000)
    public void recoverStuckProcessingRequests() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<CouponIssueRequest> stuckList =
                couponIssueService.findStuckProcessingRequests(threshold);

        if (stuckList.isEmpty()) return;

        log.warn("[Recovery] stuck PROCESSING 레코드 감지: {}건", stuckList.size());

        for (CouponIssueRequest req : stuckList) {
            try {
                // DB 상태를 REQUESTED로 되돌려 재처리 가능 상태로 변경
                couponIssueService.resetToRequested(req.getRequestId());

                CouponIssueEventDto event = new CouponIssueEventDto(
                        req.getCampaign().getCouponId(),
                        req.getUserId(),
                        req.getRequestId()
                );
                // campaign type에 따라 원래 토픽으로 재발행
                kafkaProducingService.republishToOriginalTopic(event, req.getCampaign().getType());

                log.info("[Recovery] 재발행 완료 - requestId: {}, userId: {}, type: {}",
                        req.getRequestId(), req.getUserId(), req.getCampaign().getType());
            } catch (Exception e) {
                log.error("[Recovery] 재발행 실패 - requestId: {}, cause: {}",
                        req.getRequestId(), e.getMessage());
            }
        }
    }

    // ⚠️ NOTE: REQUESTED stuck은 상태를 변경하지 않고 Kafka만 재발행
    //          PROCESSING과 달리 DB 상태 변경 없음 — Kafka 발행 실패로 인한 고착 복구
    @Scheduled(fixedDelay = 60000)
    public void recoverStuckRequestedRequests() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<CouponIssueRequest> stuckList =
                couponIssueService.findStuckRequestedRequests(threshold);

        if (stuckList.isEmpty()) return;

        log.warn("[Recovery] stuck REQUESTED 레코드 감지: {}건", stuckList.size());

        for (CouponIssueRequest req : stuckList) {
            try {
                CouponIssueEventDto event = new CouponIssueEventDto(
                        req.getCampaign().getCouponId(),
                        req.getUserId(),
                        req.getRequestId()
                );
                // 상태 변경 없이 Kafka만 재발행
                kafkaProducingService.republishToOriginalTopic(event, req.getCampaign().getType());

                log.info("[Recovery] REQUESTED 재발행 완료 - requestId: {}, userId: {}, type: {}",
                        req.getRequestId(), req.getUserId(), req.getCampaign().getType());
            } catch (Exception e) {
                log.error("[Recovery] REQUESTED 재발행 실패 - requestId: {}, cause: {}",
                        req.getRequestId(), e.getMessage());
            }
        }
    }
}
