package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.UserCoupon;
import com.eCommerce.couponDomain.entity.enums.*;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final CouponIssueService couponIssueService;
    private final CouponIssueOutboxService couponIssueOutboxService;
    private final KafkaProducingService kafkaProducingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // ══════════════════════════════════════════════════════════════
    // 메인 배치 리스너
    // ══════════════════════════════════════════════════════════════

    @KafkaListener(topics = "first-coupon-issue-requested",
            groupId = "coupon-group",
            containerFactory = "batchContainerFactory")
    public void consumeFristIssueRequest(List<CouponIssueEventDto> events) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = "success";

        try {
            log.info("[BATCH] {} 건 수신", events.size());

            // 배치 수신 건수
            meterRegistry.counter("coupon.batch.received",
                    "topic", "first-coupon-issue-requested").increment(events.size());

            // STEP 1: 사전 검증 — Pre-fetch 방식 (4쿼리 총합, 기존 N×6 대비)
            Timer.Sample step1Sample = Timer.start(meterRegistry);
            List<BatchItem> validItems = step1ValidationBatch(events, "first-coupon-issue-requested");
            step1Sample.stop(meterRegistry.timer("coupon.step.time",
                    "step", "step1", "topic", "first-coupon-issue-requested"));

            // 검증 통과/실패 건수
            meterRegistry.counter("coupon.batch.validated",
                    "topic", "first-coupon-issue-requested").increment(validItems.size());
            meterRegistry.counter("coupon.batch.validation.failed",
                    "topic", "first-coupon-issue-requested").increment(events.size() - validItems.size());

            if (validItems.isEmpty()) {
                log.info("[BATCH] 검증 통과 건 없음 — skip");
                return;
            }

            // STEP 2: 유저 쿠폰 벌크 저장
            Timer.Sample step2Sample = Timer.start(meterRegistry);
            step2SaveUserCouponBatch(validItems);
            step2Sample.stop(meterRegistry.timer("coupon.step.time",
                    "step", "step2_batch", "topic", "first-coupon-issue-requested"));

            // STEP 3: 상태/로그 벌크 업데이트
            Timer.Sample step3Sample = Timer.start(meterRegistry);
            boolean step3Success = step3UpdateStatusLogBatch(validItems);
            step3Sample.stop(meterRegistry.timer("coupon.step.time",
                    "step", "step3_batch", "topic", "first-coupon-issue-requested"));

            // STEP 4: 발급 완료 이벤트 발행 (건별) — step3 실패 시 recovery가 대신 처리하므로 skip
            if (step3Success) {
                Timer.Sample step4Sample = Timer.start(meterRegistry);
                for (BatchItem item : validItems) {
                    step4SendCompleteEvent(item.event());
                }
                step4Sample.stop(meterRegistry.timer("coupon.step.time",
                        "step", "step4", "topic", "first-coupon-issue-requested"));
            }

            log.info("[BATCH] 처리 완료 - 수신: {}, 처리: {}", events.size(), validItems.size());
        } catch (Exception e) {
            status = "error";
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(
                    "coupon.consumer.process.time",
                    "topic", "first-coupon-issue-requested",
                    "status", status
            ));
        }
    }

    @KafkaListener(topics = "open-coupon-issue-requested",
            groupId = "coupon-group-open",
            containerFactory = "batchContainerFactory")
    public void consumeOpenIssueRequest(List<CouponIssueEventDto> events) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = "success";

        try {
            log.info("[BATCH] {} 건 수신 - topic: open-coupon-issue-requested", events.size());

            meterRegistry.counter("coupon.batch.received",
                    "topic", "open-coupon-issue-requested").increment(events.size());

            // STEP 1: 사전 검증 — Pre-fetch 방식 (4쿼리 총합, 기존 N×6 대비)
            Timer.Sample step1Sample = Timer.start(meterRegistry);
            List<BatchItem> validItems = step1ValidationBatch(events, "open-coupon-issue-requested");
            step1Sample.stop(meterRegistry.timer("coupon.step.time",
                    "step", "step1", "topic", "open-coupon-issue-requested"));

            meterRegistry.counter("coupon.batch.validated",
                    "topic", "open-coupon-issue-requested").increment(validItems.size());
            meterRegistry.counter("coupon.batch.validation.failed",
                    "topic", "open-coupon-issue-requested").increment(events.size() - validItems.size());

            if (validItems.isEmpty()) {
                log.info("[BATCH] 검증 통과 건 없음 — skip");
                return;
            }

            Timer.Sample step2Sample = Timer.start(meterRegistry);
            step2SaveUserCouponBatch(validItems);
            step2Sample.stop(meterRegistry.timer("coupon.step.time",
                    "step", "step2_batch", "topic", "open-coupon-issue-requested"));

            Timer.Sample step3Sample = Timer.start(meterRegistry);
            boolean step3Success = step3UpdateStatusLogBatch(validItems);
            step3Sample.stop(meterRegistry.timer("coupon.step.time",
                    "step", "step3_batch", "topic", "open-coupon-issue-requested"));

            // STEP 4: step3 실패 시 recovery가 대신 처리하므로 skip
            if (step3Success) {
                Timer.Sample step4Sample = Timer.start(meterRegistry);
                for (BatchItem item : validItems) {
                    step4SendCompleteEvent(item.event());
                }
                step4Sample.stop(meterRegistry.timer("coupon.step.time",
                        "step", "step4", "topic", "open-coupon-issue-requested"));
            }

            log.info("[BATCH] 처리 완료 - 수신: {}, 처리: {}", events.size(), validItems.size());
        } catch (Exception e) {
            status = "error";
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(
                    "coupon.consumer.process.time",
                    "topic", "open-coupon-issue-requested",
                    "status", status
            ));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 상태 기반 복구 리스너 (Recovery)
    // ══════════════════════════════════════════════════════════════

    @KafkaListener(topics = {
            "coupon-issue-recovery",
    }, groupId = "coupon-group-retry", containerFactory = "retryContainerFactory")
    public void retryRecover(CouponIssueRetryEventDto retryEvent) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("[RETRY RECOVER] 복구 진입 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());

        // recovery 진입 카운터
        meterRegistry.counter("coupon.recovery.received").increment();

        CouponIssueEventDto event = new CouponIssueEventDto(
                retryEvent.couponId(), retryEvent.userId(), retryEvent.couponIssueRequestId());

        // ⚠️ NOTE: UpdateRetry 예외를 여기서 삼킴 — 탈출 시 Kafka DefaultErrorHandler가
        //          retryCount 증가 없이 동일 메시지를 무한 재시도하는 루프를 차단하기 위함
        boolean canRetry;
        try {
            canRetry = couponIssueService.UpdateRetry(
                    retryEvent.couponIssueRequestId(), retryEvent.failReason());
        } catch (Exception e) {
            log.error("[RETRY RECOVER FAIL] UpdateRetry 실패 - couponIssueRequestId: {}, cause: {}",
                    retryEvent.couponIssueRequestId(), e.getMessage());
            meterRegistry.counter("coupon.recovery.failed").increment();
            sample.stop(meterRegistry.timer("coupon.recovery.time", "result", "failed"));
            return;
        }

        if (!canRetry) {
            log.warn("[RETRY RECOVER] 재시도 횟수 초과 - allFail 발행, couponIssueRequestId: {}",
                    retryEvent.couponIssueRequestId());
            meterRegistry.counter("coupon.recovery.allfail").increment();
            kafkaProducingService.sendAllFail(event, retryEvent.failReason());
            sample.stop(meterRegistry.timer("coupon.recovery.time", "result", "allfail"));
            return;
        }

        try {
            // ⚠️ NOTE: 각 step 메서드는 내부에서 예외를 catch하고 sendRecovery 후 false/null 반환
            //          이 catch는 step 메서드가 아닌 recoverFromCurrentState 상단의
            //          existsUserCoupon / getIssueRequestStatus DB 조회 실패만 커버
            recoverFromCurrentState(event);
            meterRegistry.counter("coupon.recovery.success").increment();
            sample.stop(meterRegistry.timer("coupon.recovery.time", "result", "success"));
        } catch (Exception e) {
            log.error("[RETRY RECOVER FAIL] 상태 조회 실패 - couponIssueRequestId: {}, cause: {}",
                    event.couponIssueRequestId(), e.getMessage());
            meterRegistry.counter("coupon.recovery.failed").increment();
            kafkaProducingService.sendRecovery(event, e.getMessage());
            sample.stop(meterRegistry.timer("coupon.recovery.time", "result", "failed"));
        }
    }

    private void recoverFromCurrentState(CouponIssueEventDto event) {
        boolean userCouponExists = couponIssueService.existsUserCoupon(
                event.couponId(), event.userId());
        IssueRequestStatus currentStatus =
                couponIssueService.getIssueRequestStatus(event.couponIssueRequestId());

        if (userCouponExists && currentStatus == IssueRequestStatus.ISSUED) {
            log.info("[RECOVER] 이미 완료 - skip, couponIssueRequestId: {}",
                    event.couponIssueRequestId());
            meterRegistry.counter("coupon.recovery.case", "case", "A_skip").increment();
            recoverStep4IfNeeded(event);
            return;
        }

        if (userCouponExists && currentStatus != IssueRequestStatus.ISSUED) {
            log.info("[RECOVER] 쿠폰 존재, 상태 미반영 - step3/4 실행, couponIssueRequestId: {}",
                    event.couponIssueRequestId());
            meterRegistry.counter("coupon.recovery.case", "case", "B_step3_4").increment();
            step3UpdateStatusLog(event);
            step4SendCompleteEvent(event);
            return;
        }

        log.info("[RECOVER] 쿠폰 미저장 - 전체 재처리, couponIssueRequestId: {}",
                event.couponIssueRequestId());
        meterRegistry.counter("coupon.recovery.case", "case", "C_full").increment();

        CouponCampaign campaign = step1Validation(event);
        if (campaign == null) return;

        if (!step2SaveUserCoupon(event, campaign)) return;
        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    private void recoverStep4IfNeeded(CouponIssueEventDto event) {
        try {
            boolean isPublished = couponIssueOutboxService.isPublished(event.couponIssueRequestId());
            if (!isPublished) {
                log.info("[RECOVER] Outbox 미발행 → step4 실행, couponIssueRequestId: {}",
                        event.couponIssueRequestId());
                step4SendCompleteEvent(event);
            }
        } catch (Exception e) {
            log.warn("[RECOVER] Outbox 확인 실패 - 스케줄러가 처리 예정, couponIssueRequestId: {}",
                    event.couponIssueRequestId());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private 단계별 실행 메서드
    // ════════════════════════════════════════════════════════════════════════

    // ⚠️ NOTE: 배치 전용 Step1 — 3가지 검증(EventLog 존재/SUCCESS, 기발급 쌍, 캠페인 존재)을
    //          루프 전 Pre-fetch(Q1~Q3) 후 인메모리에서 처리하고, 유효 건만 Q4 벌크 PROCESSING 업데이트
    //          recovery 경로 단건 처리는 기존 step1Validation() 메서드를 그대로 사용
    private List<BatchItem> step1ValidationBatch(List<CouponIssueEventDto> events, String topic) {
        List<Long> requestIds = events.stream().map(CouponIssueEventDto::couponIssueRequestId).toList();
        List<Long> couponIds  = events.stream().map(CouponIssueEventDto::couponId).distinct().toList();
        List<String> userIds  = events.stream().map(CouponIssueEventDto::userId).toList();

        // Q1: 배치 전체 최신 EventLog 1쿼리 조회
        Map<Long, CouponEventLog> latestLogMap = couponIssueService.findLatestEventLogMap(requestIds);
        // Q2: 배치 전체 캠페인 1쿼리 조회
        Map<Long, CouponCampaign> campaignMap = couponIssueService.findAllCouponsByIds(couponIds);
        // Q3: 배치 전체 기발급 쌍 1쿼리 조회
        Set<String> issuedPairs = couponIssueService.findAlreadyIssuedPairs(couponIds, userIds);

        log.info("[STEP 1 BATCH] pre-fetch 완료 - 수신: {}, logMap: {}, campaignMap: {}, issuedPairs: {}",
                events.size(), latestLogMap.size(), campaignMap.size(), issuedPairs.size());

        List<BatchItem> validItems   = new ArrayList<>();
        List<Long> validRequestIds   = new ArrayList<>();

        for (CouponIssueEventDto event : events) {
            Long requestId = event.couponIssueRequestId();

            // EventLog 없음 → 유효하지 않은 requestId
            CouponEventLog eventLog = latestLogMap.get(requestId);
            if (eventLog == null) {
                log.warn("[STEP 1] EventLog 없음 → recovery 발행, requestId: {}", requestId);
                meterRegistry.counter("coupon.step.failed", "step", "step1").increment();
                kafkaProducingService.sendRecovery(event, "존재하지 않는 이벤트 요청입니다 requestId=" + requestId);
                continue;
            }

            // 이미 SUCCESS → 중복 이벤트 skip
            if (eventLog.getProcessingStatus() == EventProcessingStatus.SUCCESS) {
                log.info("[STEP 1] 이미 처리된 이벤트 - skip, requestId: {}", requestId);
                meterRegistry.counter("coupon.step.skipped", "step", "step1", "reason", "duplicated_event").increment();
                continue;
            }

            // 기발급 쌍 포함 → 중복 발급 skip
            if (issuedPairs.contains(event.couponId() + ":" + event.userId())) {
                log.info("[STEP 1] 이미 발급된 쿠폰 - skip, requestId: {}", requestId);
                couponIssueService.failAlreadyHadUserCoupon(requestId);
                meterRegistry.counter("coupon.step.skipped", "step", "step1", "reason", "duplicated_coupon").increment();
                continue;
            }

            // 캠페인 없음 → recovery 발행 + skip
            CouponCampaign campaign = campaignMap.get(event.couponId());
            if (campaign == null) {
                log.error("[STEP 1 FAIL] 존재하지 않는 쿠폰 - recovery 발행, couponId: {}", event.couponId());
                meterRegistry.counter("coupon.step.failed", "step", "step1").increment();
                kafkaProducingService.sendRecovery(event, "존재하지 않는 쿠폰입니다 couponId=" + event.couponId());
                continue;
            }

            validItems.add(new BatchItem(event, campaign));
            validRequestIds.add(requestId);
        }

        // Q4: 유효 건 PROCESSING 상태 벌크 업데이트 (1 UPDATE)
        if (!validRequestIds.isEmpty()) {
            couponIssueService.bulkUpdateIssueRequestStatus(validRequestIds, IssueRequestStatus.PROCESSING);
        }

        log.info("[STEP 1 BATCH] 검증 완료 - 유효: {} / 전체: {}", validItems.size(), events.size());
        return validItems;
    }

    private CouponCampaign step1Validation(CouponIssueEventDto event) {
        log.info("[STEP 1] 사전 검증 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            couponIssueService.checkAlreadyEvent(event.couponIssueRequestId());
            couponIssueService.checkAlreadyIssuedUserCoupon(event.couponId(), event.userId(), event.couponIssueRequestId());
            couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.PROCESSING);
            CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());
            log.info("[STEP 1] 사전 검증 완료 - campaignName: {}", campaign.getName());
            return campaign;
        } catch (CouponIssueException e) {
            if (e.getErrorCode() == ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT) {
                log.info("[STEP 1] 이미 처리된 이벤트 - skip, couponIssueRequestId: {}", event.couponIssueRequestId());
                meterRegistry.counter("coupon.step.skipped", "step", "step1", "reason", "duplicated_event").increment();
                return null;
            }
            if (e.getErrorCode() == ErrorCode.DUPLICATED_COUPON_ISSUE) {
                log.info("[STEP 1] 이미 발급된 쿠폰 - skip, couponIssueRequestId: {}", event.couponIssueRequestId());
                // ⚠️ NOTE: 프록시를 통한 직접 호출로 REQUIRES_NEW 트랜잭션이 정상 동작
                couponIssueService.failAlreadyHadUserCoupon(event.couponIssueRequestId());
                meterRegistry.counter("coupon.step.skipped", "step", "step1", "reason", "duplicated_coupon").increment();
                return null;
            }
            log.error("[STEP 1 FAIL] 사전 검증 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step1").increment();
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[STEP 1 FAIL] 사전 검증 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step1").increment();
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return null;
        }
    }

    private record BatchItem(CouponIssueEventDto event, CouponCampaign campaign) {}

    private void step2SaveUserCouponBatch(List<BatchItem> items) {
        log.info("[STEP 2 BATCH] 벌크 저장 시작 - {} 건", items.size());

        List<UserCoupon> userCoupons = new ArrayList<>();
        List<OutboxEvent> outboxEvents = new ArrayList<>();

        for (BatchItem item : items) {
            CouponIssueEventDto event = item.event();
            CouponCampaign campaign = item.campaign();

            userCoupons.add(UserCoupon.builder()
                    .userId(event.userId())
                    .couponId(event.couponId())
                    .couponCode(UUID.randomUUID().toString())
                    .status(UserCouponStatus.ISSUED)
                    .expiredAt(campaign.getEndAt())
                    .build());

            try {
                outboxEvents.add(OutboxEvent.builder()
                        .aggregateType("CouponIssueRequest")
                        .aggregateId(event.couponIssueRequestId())
                        .eventType("SAVE_USER_COUPON")
                        .payload(objectMapper.writeValueAsString(event))
                        .publishStatus(OutboxPublishStatus.PENDING)
                        .build());
            } catch (JsonProcessingException e) {
                log.error("[STEP 2] OutboxEvent payload 직렬화 실패 - {}",
                        event.couponIssueRequestId(), e);
            }
        }

        try {
            couponIssueService.saveAllUserCouponsAndOutbox(userCoupons, outboxEvents);
            meterRegistry.counter("coupon.step.success", "step", "step2_batch").increment(items.size());
            log.info("[STEP 2 BATCH] 벌크 저장 완료 - {} 건", items.size());
        } catch (Exception e) {
            log.error("[STEP 2 BATCH FAIL] 벌크 저장 실패 - {} 건, cause: {}",
                    items.size(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step2_batch").increment(items.size());
            for (BatchItem item : items) {
                kafkaProducingService.sendRecovery(item.event(), e.getMessage());
            }
            throw e;
        }
    }

    // ⚠️ NOTE: 벌크 처리용 step3 — 건별 루프 대신 DB 왕복 3~4번으로 처리
    //          실패 시 전체 items에 recovery 발행 후 false 반환 → 호출부에서 step4 건너뜀
    //          step3 실패 상태에서 step4까지 실행하면 recovery 경로와 중복 complete 이벤트 발생
    private boolean step3UpdateStatusLogBatch(List<BatchItem> items) {
        log.info("[STEP 3 BATCH] 상태/로그 벌크 업데이트 시작 - {} 건", items.size());
        try {
            List<Long> requestIds = items.stream()
                    .map(item -> item.event().couponIssueRequestId())
                    .toList();

            List<Long> couponIds = items.stream()
                    .map(item -> item.event().couponId())
                    .distinct()
                    .toList();

            // 1번: 이벤트 로그 벌크 INSERT (SUCCESS)
            couponIssueService.bulkUpdateEventLogStatus(requestIds, EventProcessingStatus.SUCCESS);

            // 2번: 발급 요청 상태 벌크 UPDATE
            couponIssueService.bulkUpdateIssueRequestStatus(requestIds, IssueRequestStatus.ISSUED);

            // 3번: 수량 동기화 — 쿠폰 종류별 1번씩만 (OPEN 타입 제외)
            for (Long couponId : couponIds) {
                CouponCampaign campaign = couponIssueService.findCoupon(couponId);
                if (campaign.getType() != CampaignType.OPEN) {
                    couponIssueService.syncIssuedQuantity(couponId);
                }
            }

            meterRegistry.counter("coupon.step.success", "step", "step3_batch").increment(items.size());
            log.info("[STEP 3 BATCH] 상태/로그 벌크 업데이트 완료 - {} 건", items.size());
            return true;
        } catch (Exception e) {
            log.error("[STEP 3 BATCH FAIL] 상태/로그 벌크 업데이트 실패 - {} 건, cause: {}", items.size(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step3_batch").increment(items.size());
            for (BatchItem item : items) {
                kafkaProducingService.sendRecovery(item.event(), e.getMessage());
            }
            return false;
        }
    }

    private boolean step3UpdateStatusLog(CouponIssueEventDto event) {
        log.info("[STEP 3] 상태/로그 업데이트 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            couponIssueService.updateCouponEventLog(event.couponIssueRequestId(), EventProcessingStatus.SUCCESS);
            couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.ISSUED);
            CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());
            if (campaign.getType() != CampaignType.OPEN) {
                couponIssueService.updateIssuedQuantity(event.couponId());
            }
            meterRegistry.counter("coupon.step.success", "step", "step3").increment();
            log.info("[STEP 3] 상태/로그 업데이트 완료 - couponIssueRequestId: {}", event.couponIssueRequestId());
            return true;
        } catch (Exception e) {
            log.error("[STEP 3 FAIL] 상태/로그 업데이트 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step3").increment();
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return false;
        }
    }

    private void step4SendCompleteEvent(CouponIssueEventDto event) {
        log.info("[STEP 4] 발급 완료 이벤트 발행 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            kafkaProducingService.cosumeIssueComplete(event);
            couponIssueOutboxService.markPublishedByAggregateId(event.couponIssueRequestId());
            meterRegistry.counter("coupon.step.success", "step", "step4").increment();
            log.info("[STEP 4] 발급 완료 이벤트 발행 완료 - couponIssueRequestId: {}", event.couponIssueRequestId());
        } catch (Exception e) {
            log.error("[STEP 4 FAIL] 발급 완료 이벤트 발행 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step4").increment();
            kafkaProducingService.sendRecovery(event, e.getMessage());
        }
    }

    private boolean step2SaveUserCoupon(CouponIssueEventDto event, CouponCampaign campaign) {
        log.info("[STEP 2] 유저 쿠폰 저장 시작 - userId: {}, couponId: {}", event.userId(), event.couponId());
        try {
            couponIssueService.saveUserCoupon(UserCoupon.builder()
                            .userId(event.userId())
                            .couponId(event.couponId())
                            .couponCode(UUID.randomUUID().toString())
                            .status(UserCouponStatus.ISSUED)
                            .expiredAt(campaign.getEndAt())
                            .build(),
                    OutboxEvent.builder()
                            .aggregateType("CouponIssueRequest")
                            .aggregateId(event.couponIssueRequestId())
                            .eventType("SAVE_USER_COUPON")
                            .payload(objectMapper.writeValueAsString(event))
                            .publishStatus(OutboxPublishStatus.PENDING)
                            .build());
            meterRegistry.counter("coupon.step.success", "step", "step2_single").increment();
            log.info("[STEP 2] 유저 쿠폰 저장 완료 - userId: {}, couponId: {}", event.userId(), event.couponId());
            return true;
        } catch (Exception e) {
            log.error("[STEP 2 FAIL] 유저 쿠폰 저장 실패 - userId: {}, couponId: {}, cause: {}",
                    event.userId(), event.couponId(), e.getMessage());
            meterRegistry.counter("coupon.step.failed", "step", "step2_single").increment();
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return false;
        }
    }

    @KafkaListener(topics = "coupon-issue-complete", groupId = "coupon-group-complete")
    public void completeIssueCoupon(CouponIssueEventDto completedEvent) {
        meterRegistry.counter("coupon.issue.complete").increment();
        log.info("발급 성공: couponId: %d, userId: %s couponRequestId:%d "
                .formatted(completedEvent.couponId(), completedEvent.userId(), completedEvent.couponIssueRequestId()));
    }

    @KafkaListener(topics = "coupon-issue-all-fail", groupId = "coupon-group-all-fail", containerFactory = "retryContainerFactory")
    public void issueAllfail(CouponIssueRetryEventDto retryEvent) {
        meterRegistry.counter("coupon.issue.allfail").increment();
        log.error("[ALL FAIL] 완전 실패 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());
    }
}