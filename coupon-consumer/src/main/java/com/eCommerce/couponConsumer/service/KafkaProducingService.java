package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class KafkaProducingService {

    private final CouponIssueService couponIssueService;
    // 메인 이벤트 발행용 (발급 완료)
    private final KafkaTemplate<String, CouponIssueEventDto> kafkaTemplate;
    // ⚠️ NOTE: retry 토픽은 failReason이 포함된 별도 DTO를 사용하므로
    //          KafkaTemplate 타입을 분리해 주입한다.
    private final KafkaTemplate<String, CouponIssueRetryEventDto> retryKafkaTemplate;

    // 발급 완료 토픽 발행
    public void cosumeIssueComplete(CouponIssueEventDto event) {
        kafkaTemplate.send("coupon-issue-complete", String.valueOf(event.couponIssueRequestId()), event);
    }

    // ── 단계별 재시도 토픽 발행 ────────────────────────────────────────────────
    // ⚠️ NOTE: 각 단계 실패 시 해당 단계의 retry 토픽으로 발행하여
    //          그 단계부터만 재실행되도록 함.
    //          failReason을 함께 실어 컨슈머 측에서 실패 원인을 파악할 수 있게 한다.

    public void sendRetryStep1(CouponIssueEventDto event, String failReason) {
        CouponIssueRetryEventDto retryEvent = new CouponIssueRetryEventDto(
                event.couponId(), event.userId(), event.couponIssueRequestId(), failReason);
        retryKafkaTemplate.send("coupon-issue-retry-step1", String.valueOf(event.userId()), retryEvent);
    }

    public void sendRetryStep2(CouponIssueEventDto event, String failReason) {
        CouponIssueRetryEventDto retryEvent = new CouponIssueRetryEventDto(
                event.couponId(), event.userId(), event.couponIssueRequestId(), failReason);
        retryKafkaTemplate.send("coupon-issue-retry-step2", String.valueOf(event.userId()), retryEvent);
    }

    public void sendRetryStep3(CouponIssueEventDto event, String failReason) {
        CouponIssueRetryEventDto retryEvent = new CouponIssueRetryEventDto(
                event.couponId(), event.userId(), event.couponIssueRequestId(), failReason);
        retryKafkaTemplate.send("coupon-issue-retry-step3", String.valueOf(event.userId()), retryEvent);
    }

    public void sendRetryStep4(CouponIssueEventDto event, String failReason) {
        CouponIssueRetryEventDto retryEvent = new CouponIssueRetryEventDto(
                event.couponId(), event.userId(), event.couponIssueRequestId(), failReason);
        retryKafkaTemplate.send("coupon-issue-retry-step4", String.valueOf(event.userId()), retryEvent);
    }
    // ⚠️ NOTE: retryCount >= 3 초과로 더 이상 재시도 불가 시 발행.
    //          DB allFailed() 처리는 UpdateRetry() 내부에서 이미 완료된 상태.
    public void sendAllFail(CouponIssueEventDto event, String failReason) {
        CouponIssueRetryEventDto retryEvent = new CouponIssueRetryEventDto(
                event.couponId(), event.userId(), event.couponIssueRequestId(), failReason);
        retryKafkaTemplate.send("coupon-issue-all-fail", String.valueOf(event.userId()), retryEvent);
    }
}
