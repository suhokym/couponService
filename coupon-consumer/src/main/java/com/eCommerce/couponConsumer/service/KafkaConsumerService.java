package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
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

    @KafkaListener(topics = "first-coupon-issue-requested",
            groupId = "coupon-group",
            containerFactory = "batchContainerFactory")
    public void consumeFristIssueRequest(List<CouponIssueEventDto> events) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = "success";

        try {
            log.info("[BATCH] {} 건 수신", events.size());

            // STEP 1: 사전 검증 (건별 — 기존 로직 그대로)
            List<BatchItem> validItems = new ArrayList<>();
            for (CouponIssueEventDto event : events) {
                CouponCampaign campaign = step1Validation(event);
                if (campaign != null) {
                    validItems.add(new BatchItem(event, campaign));
                }
            }

            if (validItems.isEmpty()) {
                log.info("[BATCH] 검증 통과 건 없음 — skip");
                return;
            }

            // STEP 2: 유저 쿠폰 벌크 저장 (핵심 변경)
            step2SaveUserCouponBatch(validItems);

            // STEP 3: 상태/로그 업데이트 (건별 — 기존 로직 그대로)
            for (BatchItem item : validItems) {
                step3UpdateStatusLog(item.event());
            }

            // STEP 4: 발급 완료 이벤트 발행 (건별 — 기존 로직 그대로)
            for (BatchItem item : validItems) {
                step4SendCompleteEvent(item.event());
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

            List<BatchItem> validItems = new ArrayList<>();
            for (CouponIssueEventDto event : events) {
                CouponCampaign campaign = step1Validation(event);
                if (campaign != null) {
                    validItems.add(new BatchItem(event, campaign));
                }
            }

            if (validItems.isEmpty()) {
                log.info("[BATCH] 검증 통과 건 없음 — skip");
                return;
            }

            step2SaveUserCouponBatch(validItems);

            for (BatchItem item : validItems) {
                step3UpdateStatusLog(item.event());
            }

            for (BatchItem item : validItems) {
                step4SendCompleteEvent(item.event());
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
// 재시도 리스너: 상태 기반 복구 (Recovery)
// ══════════════════════════════════════════════════════════════
// ⚠️ NOTE: 기존 "같은 step 재실행" → "현재 상태 확인 후 남은 step만 실행"으로 변경
//          어디까지 처리됐는지 DB 상태를 보고 판단하므로
//          step별로 retry 토픽을 나눌 필요 없이 하나로 통합 가능

    // ── 통합 retry 리스너 ──
// ⚠️ NOTE: step1~4 retry 토픽을 하나로 합쳐도 되지만,
//          기존 토픽 구조 유지하면서 내부 로직만 통합
    @KafkaListener(topics = {
            "coupon-issue-recovery",
    }, groupId = "coupon-group-retry", containerFactory = "retryContainerFactory")
    public void retryRecover(CouponIssueRetryEventDto retryEvent) {
        log.info("[RETRY RECOVER] 복구 진입 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());

        CouponIssueEventDto event = new CouponIssueEventDto(
                retryEvent.couponId(), retryEvent.userId(), retryEvent.couponIssueRequestId());

        // retryCount 체크 — 3회 초과 시 allFail
        boolean canRetry = couponIssueService.UpdateRetry(
                retryEvent.couponIssueRequestId(), retryEvent.failReason());
        if (!canRetry) {
            log.warn("[RETRY RECOVER] 재시도 횟수 초과 - allFail 발행, couponIssueRequestId: {}",
                    retryEvent.couponIssueRequestId());
            kafkaProducingService.sendAllFail(event, retryEvent.failReason());
            return;
        }

        try {
            recoverFromCurrentState(event);
        } catch (Exception e) {
            log.error("[RETRY RECOVER FAIL] 복구 실패 - couponIssueRequestId: {}, cause: {}",
                    event.couponIssueRequestId(), e.getMessage());
            // 실패 시 다시 retry 토픽으로 — retryCount가 관리하므로 무한루프 없음
            kafkaProducingService.sendRecovery(event, e.getMessage());
        }
    }

    // ── 현재 상태 확인 후 남은 step만 실행 ──
    private void recoverFromCurrentState(CouponIssueEventDto event) {
        // 1) 유저쿠폰이 존재하는지 확인
        boolean userCouponExists = couponIssueService.existsUserCoupon(
                event.couponId(), event.userId());

        // 2) issueRequest 현재 상태 조회
        IssueRequestStatus currentStatus =
                couponIssueService.getIssueRequestStatus(event.couponIssueRequestId());

        if (userCouponExists && currentStatus == IssueRequestStatus.ISSUED) {
            // ── 케이스 A: 전부 완료 → skip ──
            log.info("[RECOVER] 이미 완료 - skip, couponIssueRequestId: {}",
                    event.couponIssueRequestId());
            // step4(Kafka 발행)만 안 됐을 수 있으니 Outbox 확인
            recoverStep4IfNeeded(event);
            return;
        }

        if (userCouponExists && currentStatus != IssueRequestStatus.ISSUED) {
            // ── 케이스 B: 쿠폰 저장됨, 상태 미반영 → step3/4만 실행 ──
            log.info("[RECOVER] 쿠폰 존재, 상태 미반영 - step3/4 실행, couponIssueRequestId: {}",
                    event.couponIssueRequestId());
            step3UpdateStatusLog(event);
            step4SendCompleteEvent(event);
            return;
        }

        // ── 케이스 C: 쿠폰 미저장 → step1부터 전체 실행 ──
        log.info("[RECOVER] 쿠폰 미저장 - 전체 재처리, couponIssueRequestId: {}",
                event.couponIssueRequestId());

        CouponCampaign campaign = step1Validation(event);
        if (campaign == null) return;

        if (!step2SaveUserCoupon(event, campaign)) return;
        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    // ── step4 Outbox 미발행 복구 ──
// ⚠️ NOTE: ISSUED까지 완료됐지만 Kafka 발행만 실패한 케이스
//          Outbox가 PENDING이면 발행 시도, PUBLISHED면 skip
    private void recoverStep4IfNeeded(CouponIssueEventDto event) {
        try {
            boolean isPublished = couponIssueOutboxService.isPublished(event.couponIssueRequestId());
            if (!isPublished) {
                log.info("[RECOVER] Outbox 미발행 → step4 실행, couponIssueRequestId: {}",
                        event.couponIssueRequestId());
                step4SendCompleteEvent(event);
            }
        } catch (Exception e) {
            // Outbox 확인 실패해도 스케줄러가 at-least-once 보장하므로 무시
            log.warn("[RECOVER] Outbox 확인 실패 - 스케줄러가 처리 예정, couponIssueRequestId: {}",
                    event.couponIssueRequestId());
        }
    }


    // ════════════════════════════════════════════════════════════════════════
    // Private 단계별 실행 메서드
    // ════════════════════════════════════════════════════════════════════════

    // ── STEP 1: 사전 검증 (중복 확인 + 상태 PROCESSING 변경 + 캠페인 조회) ──
    private CouponCampaign step1Validation(CouponIssueEventDto event) {
        log.info("[STEP 1] 사전 검증 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            // ⚠️ NOTE: 중복 처리 여부를 먼저 확인 후 PROCESSING 세팅
            //          순서가 바뀌면 이미 ISSUED된 레코드를 PROCESSING으로 오염시킨 뒤
            //          중복을 감지해 null 반환 → status stuck 버그 발생
            couponIssueService.checkAlreadyEvent(event.couponIssueRequestId());
            couponIssueService.checkAlreadyIssuedUserCoupon(event.couponId(), event.userId(), event.couponIssueRequestId());
            log.info("PROCESSING넘어감");
            couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.PROCESSING);
            CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());

            log.info("[STEP 1] 사전 검증 완료 - campaignName: {}", campaign.getName());
            return campaign;
        } catch (CouponIssueException e) {
            if (e.getErrorCode() == ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT) {
                // 이미 처리된 요청 → 멱등성 보장, retry 없이 종료
                log.info("[STEP 1] 이미 처리된 요청 - skip, couponIssueRequestId: {}", event.couponIssueRequestId());
                kafkaProducingService.sendRecovery(event, e.getMessage());
                return null;
            }
            if (e.getErrorCode() == ErrorCode.DUPLICATED_COUPON_ISSUE) {
                // 이미 처리된 요청 → 멱등성 보장, retry 없이 종료
                log.info("[STEP 1] 이미 처리된 요청 - skip, couponIssueRequestId: {}", event.couponIssueRequestId());
                return null;
            }
            log.error("[STEP 1 FAIL] 사전 검증 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(),e.getMessage());
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return null;
        }catch (Exception e) {
            log.error("[STEP 1 FAIL] 사전 검증 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return null;
        }
    }

    private record BatchItem(CouponIssueEventDto event, CouponCampaign campaign) {}

    // ── STEP 2: 유저 쿠폰 저장 ───────────────────────────────────────────────
    private void step2SaveUserCouponBatch(List<BatchItem> items) {
        log.info("[STEP 2 BATCH] 벌크 저장 시작 - {} 건", items.size());

        List<UserCoupon> userCoupons = new ArrayList<>();
        List<OutboxEvent> outboxEvents = new ArrayList<>();

        for (BatchItem item : items) {
            CouponIssueEventDto event = item.event();
            CouponCampaign campaign = item.campaign();

            userCoupons.add(UserCoupon.builder()
                    .userId(event.userId())
                    .campaign(campaign)
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
            log.info("[STEP 2 BATCH] 벌크 저장 완료 - {} 건", items.size());
        } catch (Exception e) {
            // 벌크 실패 → 100건 각각 retry 토픽 발행
            log.error("[STEP 2 BATCH FAIL] 벌크 저장 실패 - {} 건, cause: {}",
                    items.size(), e.getMessage());
            for (BatchItem item : items) {
                kafkaProducingService.sendRecovery(item.event(), e.getMessage());
            }
            // step3/4 실행 방지
            throw e;
        }
    }

    // ── STEP 3: 상태/로그 업데이트 (이벤트 로그 SUCCESS + 상태 ISSUED + 수량 증가) ──
    // ⚠️ NOTE: OPEN 타입은 수량 제한이 없으므로 updateIssuedQuantity를 skip한다.
    //          campaignType을 Kafka 메시지에 싣지 않고 DB(CouponCampaign.type)에서 직접
    //          조회하는 이유: 메시지 유실·구버전 호환 시에도 항상 정확한 타입을 보장하기 위함.
    private boolean step3UpdateStatusLog(CouponIssueEventDto event) {
        log.info("[STEP 3] 상태/로그 업데이트 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            couponIssueService.updateCouponEventLog(event.couponIssueRequestId(), EventProcessingStatus.SUCCESS);
            couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.ISSUED);
            CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());
            if (campaign.getType() != CampaignType.OPEN) {
                couponIssueService.updateIssuedQuantity(event.couponId());
            }
            log.info("[STEP 3] 상태/로그 업데이트 완료 - couponIssueRequestId: {}", event.couponIssueRequestId());
            return true;
        } catch (Exception e) {
            log.error("[STEP 3 FAIL] 상태/로그 업데이트 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            kafkaProducingService.sendRecovery(event, e.getMessage());
            return false;
        }
    }

    // ── STEP 4: 발급 완료 Kafka 토픽 발행 ────────────────────────────────────
    // ⚠️ NOTE: 발행 성공 시 즉시 Outbox를 PUBLISHED로 변경하여 5초 스케줄러의 중복 발행을 방지한다.
    //          발행 실패 시 Outbox는 PENDING 상태로 유지 → 스케줄러가 at-least-once 전달 보장
    private void step4SendCompleteEvent(CouponIssueEventDto event) {
        log.info("[STEP 4] 발급 완료 이벤트 발행 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            kafkaProducingService.cosumeIssueComplete(event);
            couponIssueOutboxService.markPublishedByAggregateId(event.couponIssueRequestId());
            log.info("[STEP 4] 발급 완료 이벤트 발행 완료 - couponIssueRequestId: {}", event.couponIssueRequestId());
        } catch (Exception e) {
            log.error("[STEP 4 FAIL] 발급 완료 이벤트 발행 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            kafkaProducingService.sendRecovery(event, e.getMessage());
        }
    }


    @KafkaListener(topics = "coupon-issue-complete", groupId = "coupon-group-complete")
    public void completeIssueCoupon(CouponIssueEventDto completedEvent) {
            log.info("발급 성공: couponId: %d, userId: %s couponRequestId:%d "
                    .formatted(completedEvent.couponId(), completedEvent.userId(), completedEvent.couponIssueRequestId()));
    }



    @KafkaListener(topics = "coupon-issue-all-fail", groupId = "coupon-group-all-fail", containerFactory = "retryContainerFactory")
    public void issueAllfail(CouponIssueRetryEventDto retryEvent) {
        //비정상 상황
    }



}
