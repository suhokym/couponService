package com.eCommerce.couponApi.controller;

import com.eCommerce.couponApi.dto.CouponIssueKafkaDto;
import com.eCommerce.couponApi.dto.CouponIssueRequestDto;
import com.eCommerce.couponApi.dto.CouponIssueResponseDto;
import com.eCommerce.couponApi.dto.CouponIssueResultDto;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponApi.service.CouponEventProducer;
import com.eCommerce.couponApi.service.CouponRedisService;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponIssueControllerTest {

    @InjectMocks
    private CouponIssueController couponIssueController;

    @Mock
    private CouponRedisService couponRedisService;

    @Mock
    private CouponEventProducer couponEventProducer;

    private final CouponIssueRequestDto dto = new CouponIssueRequestDto(1L, "user1", null);

    // ══════════════════════════════════════════════════════════════
    // 선착순(FIRST_COME) 발급
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FIRST_COME 발급 흐름")
    class FirstCome {

        @Test
        @DisplayName("성공: SUCCESS_FIRST_COME — Kafka 발행 후 '성공' 응답")
        void success_publishesKafkaAndReturnsSuccess() {
            given(couponRedisService.issueCoupon(1L, "user1"))
                    .willReturn(Mono.just(new CouponIssueResultDto(CouponIssueRequestCode.SUCCESS_FIRST_COME, 10L)));
            given(couponEventProducer.publishFirstCouponIssuedRequest(any(CouponIssueKafkaDto.class)))
                    .willReturn(Mono.empty());

            StepVerifier.create(couponIssueController.couponIssue(dto))
                    .assertNext(response -> {
                        assertThat(response.code()).isEqualTo("SUCCESS_FIRST_COME");
                        assertThat(response.message()).isEqualTo("성공");
                    })
                    .verifyComplete();

            verify(couponEventProducer).publishFirstCouponIssuedRequest(any());
            verify(couponEventProducer, never()).publishOpenCouponIssuedRequest(any());
        }

        @Test
        @DisplayName("Kafka 발행 실패 → 에러 전파")
        void kafkaFail_propagatesError() {
            given(couponRedisService.issueCoupon(1L, "user1"))
                    .willReturn(Mono.just(new CouponIssueResultDto(CouponIssueRequestCode.SUCCESS_FIRST_COME, 10L)));
            given(couponEventProducer.publishFirstCouponIssuedRequest(any()))
                    .willReturn(Mono.error(new RuntimeException("Kafka 실패")));

            StepVerifier.create(couponIssueController.couponIssue(dto))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 오픈(OPEN) 발급
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OPEN 발급 흐름")
    class Open {

        @Test
        @DisplayName("성공: SUCCESS_OPEN — Kafka 발행 후 '성공' 응답")
        void success_publishesOpenKafkaAndReturnsSuccess() {
            given(couponRedisService.issueCoupon(1L, "user1"))
                    .willReturn(Mono.just(new CouponIssueResultDto(CouponIssueRequestCode.SUCCESS_OPEN, 20L)));
            given(couponEventProducer.publishOpenCouponIssuedRequest(any(CouponIssueKafkaDto.class)))
                    .willReturn(Mono.empty());

            StepVerifier.create(couponIssueController.couponIssue(dto))
                    .assertNext(response -> {
                        assertThat(response.code()).isEqualTo("SUCCESS_OPEN");
                        assertThat(response.message()).isEqualTo("성공");
                    })
                    .verifyComplete();

            verify(couponEventProducer).publishOpenCouponIssuedRequest(any());
            verify(couponEventProducer, never()).publishFirstCouponIssuedRequest(any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 발급 불가 코드
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("발급 불가 응답")
    class NotIssuable {

        @Test
        @DisplayName("SUCCESS_FIRST_COME/OPEN 아닌 코드 — '발급 불가' 응답, Kafka 발행 안 됨")
        // ⚠️ NOTE: DUPLICATE_COUPON_ISSUE는 실제 서비스에서 예외로 던져지지만
        //          서비스를 mock하면 컨트롤러는 code를 그대로 받아 "발급 불가" 분기 처리
        void nonSuccessCode_returnsNotIssuable_noKafka() {
            given(couponRedisService.issueCoupon(1L, "user1"))
                    .willReturn(Mono.just(new CouponIssueResultDto(CouponIssueRequestCode.FAIL, null)));

            StepVerifier.create(couponIssueController.couponIssue(dto))
                    .assertNext(response -> {
                        assertThat(response.code()).isEqualTo("FAIL");
                        assertThat(response.message()).isEqualTo("발급 불가");
                    })
                    .verifyComplete();

            verify(couponEventProducer, never()).publishFirstCouponIssuedRequest(any());
            verify(couponEventProducer, never()).publishOpenCouponIssuedRequest(any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 서비스 레이어 예외 전파
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("서비스 예외 전파")
    class ServiceError {

        @Test
        @DisplayName("CouponRedisService 예외 → 컨트롤러까지 전파")
        void serviceThrows_propagatesToController() {
            given(couponRedisService.issueCoupon(anyLong(), anyString()))
                    .willReturn(Mono.error(new CouponIssueException(
                            ErrorCode.FAIL_COUPON_ISSUE_REQUEST, "캐시 오류")));

            StepVerifier.create(couponIssueController.couponIssue(dto))
                    .expectError(CouponIssueException.class)
                    .verify();

            verify(couponEventProducer, never()).publishFirstCouponIssuedRequest(any());
            verify(couponEventProducer, never()).publishOpenCouponIssuedRequest(any());
        }
    }
}
