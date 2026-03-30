package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.dto.CouponIssueKafkaDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CouponEventProducerTest {

    @InjectMocks
    private CouponEventProducer couponEventProducer;

    @Mock
    private KafkaTemplate<String, CouponIssueKafkaDto> kafkaTemplate;

    private final CouponIssueKafkaDto sampleEvent = new CouponIssueKafkaDto(1L, "user1", 10L);

    // ══════════════════════════════════════════════════════════════
    // publishFirstCouponIssuedRequest()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("publishFirstCouponIssuedRequest()")
    class PublishFirstCoupon {

        @Test
        @DisplayName("Kafka 발행 성공 — Mono<Void> 완료")
        void success_completesEmpty() {
            CompletableFuture<SendResult<String, CouponIssueKafkaDto>> future =
                    CompletableFuture.completedFuture(null);
            given(kafkaTemplate.send(eq("first-coupon-issue-requested"), anyString(), any()))
                    .willReturn(future);

            StepVerifier.create(couponEventProducer.publishFirstCouponIssuedRequest(sampleEvent))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Kafka 발행 실패 — 예외 전파")
        void failure_propagatesError() {
            CompletableFuture<SendResult<String, CouponIssueKafkaDto>> failedFuture =
                    new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka 브로커 연결 실패"));
            given(kafkaTemplate.send(eq("first-coupon-issue-requested"), anyString(), any()))
                    .willReturn(failedFuture);

            StepVerifier.create(couponEventProducer.publishFirstCouponIssuedRequest(sampleEvent))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // publishOpenCouponIssuedRequest()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("publishOpenCouponIssuedRequest()")
    class PublishOpenCoupon {

        @Test
        @DisplayName("Kafka 발행 성공 — Mono<Void> 완료")
        void success_completesEmpty() {
            CompletableFuture<SendResult<String, CouponIssueKafkaDto>> future =
                    CompletableFuture.completedFuture(null);
            given(kafkaTemplate.send(eq("open-coupon-issue-requested"), anyString(), any()))
                    .willReturn(future);

            StepVerifier.create(couponEventProducer.publishOpenCouponIssuedRequest(sampleEvent))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Kafka 발행 실패 — 예외 전파")
        void failure_propagatesError() {
            CompletableFuture<SendResult<String, CouponIssueKafkaDto>> failedFuture =
                    new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka 브로커 다운"));
            given(kafkaTemplate.send(eq("open-coupon-issue-requested"), anyString(), any()))
                    .willReturn(failedFuture);

            StepVerifier.create(couponEventProducer.publishOpenCouponIssuedRequest(sampleEvent))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }
}
