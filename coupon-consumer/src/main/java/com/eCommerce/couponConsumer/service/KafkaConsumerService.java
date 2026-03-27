package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.UserCoupon;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.entity.enums.UserCouponStatus;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final KafkaTemplate<String, CouponIssueEventDto> kafkaTemplate;
    private final CouponIssueService couponIssueService;
    private final CouponIssueOutboxService couponIssueOutboxService;
    private final KafkaProducingService kafkaProducingService;


    // ── 메인 리스너: STEP 1 → 2 → 3 → 4 순서로 전체 실행 ────────────────────
    @KafkaListener(topics = "first-coupon-issue-requested", groupId = "coupon-group")
    public void consumeFristIssueRequest(CouponIssueEventDto event) {
        log.info("[CONSUME] 메시지 수신 - couponIssueRequestId: {}, couponId: {}, userId: {}",
                event.couponIssueRequestId(), event.couponId(), event.userId());

        CouponCampaign campaign = step1Validation(event);
        if (campaign == null) return;

        if (!step2SaveUserCoupon(event, campaign)) return;
        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    // ── 오픈 쿠폰 메인 리스너: STEP 1 → 2 → 3 → 4 순서로 전체 실행 ────────────────────
    @KafkaListener(topics = "open-coupon-issue-requested", groupId = "coupon-group-open")
    public void consumeOpenIssueRequest(CouponIssueEventDto event) {
        log.info("[CONSUME] 메시지 수신 - couponIssueRequestId: {}, couponId: {}, userId: {}",
                event.couponIssueRequestId(), event.couponId(), event.userId());

        CouponCampaign campaign = step1Validation(event);
        if (campaign == null) return;

        if (!step2SaveUserCoupon(event, campaign)) return;
        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    // ── 재시도 리스너: STEP 1 실패 시 재진입 ─────────────────────────────────
    @KafkaListener(topics = "coupon-issue-retry-step1", groupId = "coupon-group-retry")
    public void retryStep1(CouponIssueRetryEventDto retryEvent) {
        log.info("[RETRY STEP 1] 재시도 진입 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());

        // retry DTO에서 원본 이벤트 필드만 추출하여 기존 step 메서드 재사용
        CouponIssueEventDto event = new CouponIssueEventDto(
                retryEvent.couponId(), retryEvent.userId(), retryEvent.couponIssueRequestId());

        // ⚠️ NOTE: false 반환 = retryCount >= 3, DB allFailed() 완료
        //          → allFail 토픽 발행 후 종료
        boolean canRetry = couponIssueService.UpdateRetry(retryEvent.couponIssueRequestId(), retryEvent.failReason());
        if (!canRetry) {
            log.warn("[RETRY STEP 1] 재시도 횟수 초과 - allFail 발행, couponIssueRequestId: {}",
                    retryEvent.couponIssueRequestId());
            kafkaProducingService.sendAllFail(event, retryEvent.failReason());
            return;
        }

        CouponCampaign campaign = step1Validation(event);
        if (campaign == null) return;

        if (!step2SaveUserCoupon(event, campaign)) return;
        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    // ── 재시도 리스너: STEP 2 실패 시 재진입 (캠페인 재조회 후 step2부터) ─────
    @KafkaListener(topics = "coupon-issue-retry-step2", groupId = "coupon-group-retry")
    public void retryStep2(CouponIssueRetryEventDto retryEvent) {
        log.info("[RETRY STEP 2] 재시도 진입 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());

        CouponIssueEventDto event = new CouponIssueEventDto(
                retryEvent.couponId(), retryEvent.userId(), retryEvent.couponIssueRequestId());

        // ⚠️ NOTE: false 반환 = retryCount >= 3, DB allFailed() 완료
        //          → allFail 토픽 발행 후 종료
        boolean canRetry = couponIssueService.UpdateRetry(retryEvent.couponIssueRequestId(), retryEvent.failReason());
        if (!canRetry) {
            log.warn("[RETRY STEP 2] 재시도 횟수 초과 - allFail 발행, couponIssueRequestId: {}",
                    retryEvent.couponIssueRequestId());
            kafkaProducingService.sendAllFail(event, retryEvent.failReason());
            return;
        }

        CouponCampaign campaign;
        try {
            campaign = couponIssueService.findCoupon(event.couponId());
        } catch (Exception e) {
            log.error("[RETRY STEP 2 FAIL] 캠페인 재조회 실패 - couponId: {}, cause: {}", event.couponId(), e.getMessage());
            kafkaProducingService.sendRetryStep2(event, e.getMessage());
            return;
        }

        if (!step2SaveUserCoupon(event, campaign)) return;
        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    // ── 재시도 리스너: STEP 3 실패 시 재진입 ─────────────────────────────────
    @KafkaListener(topics = "coupon-issue-retry-step3", groupId = "coupon-group-retry")
    public void retryStep3(CouponIssueRetryEventDto retryEvent) {
        log.info("[RETRY STEP 3] 재시도 진입 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());

        CouponIssueEventDto event = new CouponIssueEventDto(
                retryEvent.couponId(), retryEvent.userId(), retryEvent.couponIssueRequestId());

        // ⚠️ NOTE: false 반환 = retryCount >= 3, DB allFailed() 완료
        //          → allFail 토픽 발행 후 종료
        boolean canRetry = couponIssueService.UpdateRetry(retryEvent.couponIssueRequestId(), retryEvent.failReason());
        if (!canRetry) {
            log.warn("[RETRY STEP 3] 재시도 횟수 초과 - allFail 발행, couponIssueRequestId: {}",
                    retryEvent.couponIssueRequestId());
            kafkaProducingService.sendAllFail(event, retryEvent.failReason());
            return;
        }

        if (!step3UpdateStatusLog(event)) return;
        step4SendCompleteEvent(event);
    }

    // ── 재시도 리스너: STEP 4 실패 시 재진입 ─────────────────────────────────
    @KafkaListener(topics = "coupon-issue-retry-step4", groupId = "coupon-group-retry")
    public void retryStep4(CouponIssueRetryEventDto retryEvent) {
        log.info("[RETRY STEP 4] 재시도 진입 - couponIssueRequestId: {}, failReason: {}",
                retryEvent.couponIssueRequestId(), retryEvent.failReason());

        CouponIssueEventDto event = new CouponIssueEventDto(
                retryEvent.couponId(), retryEvent.userId(), retryEvent.couponIssueRequestId());

        // ⚠️ NOTE: false 반환 = retryCount >= 3, DB allFailed() 완료
        //          → allFail 토픽 발행 후 종료
        boolean canRetry = couponIssueService.UpdateRetry(retryEvent.couponIssueRequestId(), retryEvent.failReason());
        if (!canRetry) {
            log.warn("[RETRY STEP 4] 재시도 횟수 초과 - allFail 발행, couponIssueRequestId: {}",
                    retryEvent.couponIssueRequestId());
            kafkaProducingService.sendAllFail(event, retryEvent.failReason());
            return;
        }

        step4SendCompleteEvent(event);
    }


    // ════════════════════════════════════════════════════════════════════════
    // Private 단계별 실행 메서드
    // ════════════════════════════════════════════════════════════════════════

    // ── STEP 1: 사전 검증 (상태 PROCESSING 변경 + 캠페인 조회 + 중복 확인) ──
    private CouponCampaign step1Validation(CouponIssueEventDto event) {
        log.info("[STEP 1] 사전 검증 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.PROCESSING);
            CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());
            couponIssueService.checkAlreadyEvent(event.couponIssueRequestId());
            couponIssueService.checkAlreadyIssuedUserCoupon(event.couponId(), event.userId(), event.couponIssueRequestId());
            log.info("[STEP 1] 사전 검증 완료 - campaignName: {}", campaign.getName());
            return campaign;
        } catch (Exception e) {
            log.error("[STEP 1 FAIL] 사전 검증 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            kafkaProducingService.sendRetryStep1(event, e.getMessage());
            return null;
        }
    }

    // ── STEP 2: 유저 쿠폰 저장 ───────────────────────────────────────────────
    private boolean step2SaveUserCoupon(CouponIssueEventDto event, CouponCampaign campaign) {
        log.info("[STEP 2] 유저 쿠폰 저장 시작 - userId: {}, couponId: {}", event.userId(), event.couponId());
        try {
            couponIssueService.saveUserCoupon(UserCoupon.builder()
                    .userId(event.userId())
                    .campaign(campaign)
                    .couponCode(UUID.randomUUID().toString()) // 고유 쿠폰 코드 생성
                    .status(UserCouponStatus.ISSUED)
                    .expiredAt(campaign.getEndAt())
                    .build());
            log.info("[STEP 2] 유저 쿠폰 저장 완료 - userId: {}, couponId: {}", event.userId(), event.couponId());
            return true;
        } catch (Exception e) {
            log.error("[STEP 2 FAIL] 유저 쿠폰 저장 실패 - userId: {}, couponId: {}, cause: {}", event.userId(), event.couponId(), e.getMessage());
            kafkaProducingService.sendRetryStep2(event, e.getMessage());
            return false;
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
            kafkaProducingService.sendRetryStep3(event, e.getMessage());
            return false;
        }
    }

    // ── STEP 4: 발급 완료 Kafka 토픽 발행 ────────────────────────────────────
    private void step4SendCompleteEvent(CouponIssueEventDto event) {
        log.info("[STEP 4] 발급 완료 이벤트 발행 시작 - couponIssueRequestId: {}", event.couponIssueRequestId());
        try {
            kafkaProducingService.cosumeIssueComplete(event);
            log.info("[STEP 4] 발급 완료 이벤트 발행 완료 - couponIssueRequestId: {}", event.couponIssueRequestId());
        } catch (Exception e) {
            log.error("[STEP 4 FAIL] 발급 완료 이벤트 발행 실패 - couponIssueRequestId: {}, cause: {}", event.couponIssueRequestId(), e.getMessage());
            kafkaProducingService.sendRetryStep4(event, e.getMessage());
        }
    }


    @KafkaListener(topics = "coupon-issue-complete", groupId = "coupon-group-complete")
    public void completeIssueCoupon(CouponIssueEventDto completedEvent) {
            log.info("발급 성공: couponId: %d, userId: %s couponRequestId:%d "
                    .formatted(completedEvent.couponId(), completedEvent.userId(), completedEvent.couponIssueRequestId()));
    }
}
