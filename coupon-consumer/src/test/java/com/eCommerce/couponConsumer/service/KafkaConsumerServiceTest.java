package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import com.eCommerce.couponDomain.service.CouponIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    @Mock
    private CouponIssueService couponIssueService;

    @Mock
    private CouponIssueOutboxService couponIssueOutboxService;

    @Mock
    private KafkaTemplate<String, CouponIssueEventDto> kafkaTemplate;

    // ⚠️ NOTE: KafkaProducingService를 @Mock으로 등록해야 step 실패 시 NPE를 방지한다.
    //          없으면 예외 catch 후 sendRetryStepX 호출 시 NullPointerException 발생.
    @Mock
    private KafkaProducingService kafkaProducingService;

    // ⚠️ NOTE: step2에서 OutboxEvent payload 직렬화에 사용되므로 mock 필요
    @Mock
    private ObjectMapper objectMapper;

    private static final long COUPON_ID = 100L;
    private static final String USER_ID = "user1";
    private static final long REQUEST_ID = 1L;

    private CouponIssueEventDto event;
    private CouponIssueRetryEventDto retryEvent;
    private CouponCampaign firstComeCampaign;
    private CouponCampaign openCampaign;

    @BeforeEach
    void setUp() {
        event = new CouponIssueEventDto(COUPON_ID, USER_ID, REQUEST_ID);
        retryEvent = new CouponIssueRetryEventDto(COUPON_ID, USER_ID, REQUEST_ID, "테스트 실패");

        firstComeCampaign = CouponCampaign.builder()
                .name("선착순 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        openCampaign = CouponCampaign.builder()
                .name("오픈 캠페인")
                .type(CampaignType.OPEN)
                .status(CampaignStatus.ACTIVE)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // 메인 리스너: FIRST_COME
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FIRST_COME 메인 리스너")
    class FirstComeMainListener {

        @Test
        @DisplayName("정상 흐름: step1~4 모두 실행되고 updateIssuedQuantity 호출됨")
        void success_updatesIssuedQuantity() {
            // ⚠️ NOTE: step1(findCoupon) + step3(findCoupon) = 총 2회 호출
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(couponIssueService).saveUserCoupon(any(), any());
            verify(couponIssueService).updateCouponEventLog(REQUEST_ID, EventProcessingStatus.SUCCESS);
            verify(couponIssueService).updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.ISSUED);
            // FIRST_COME은 수량 증가 필수
            verify(couponIssueService).updateIssuedQuantity(COUPON_ID);
            verify(kafkaProducingService).cosumeIssueComplete(event);
        }

        @Test
        @DisplayName("step3에서 findCoupon을 재호출해 캠페인 타입을 DB에서 확인한다")
        void step3_callsFindCouponAgainForTypeCheck() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.consumeFristIssueRequest(event);

            // step1 1회 + step3 1회 = 합계 2회
            verify(couponIssueService, times(2)).findCoupon(COUPON_ID);
        }

        @Test
        @DisplayName("step1: findCoupon 실패 → sendRetryStep1 발행, step2 이후 실행 안 됨")
        void step1_findCouponFails_sendsRetryAndStops() {
            willThrow(new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 없음"))
                    .given(couponIssueService).findCoupon(COUPON_ID);

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep1(eq(event), any());
            verify(couponIssueService, never()).saveUserCoupon(any(), any());
            verify(couponIssueService, never()).updateCouponEventLog(any(), any());
            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
        }

        @Test
        @DisplayName("step1: checkAlreadyEvent 실패 → sendRetryStep1 발행, step2 실행 안 됨")
        void step1_checkAlreadyEventFails_sendsRetry() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "중복 이벤트"))
                    .given(couponIssueService).checkAlreadyEvent(REQUEST_ID);

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep1(eq(event), any());
            verify(couponIssueService, never()).saveUserCoupon(any(), any());
        }

        @Test
        @DisplayName("step1: checkAlreadyIssuedUserCoupon 실패 → sendRetryStep1 발행, step2 실행 안 됨")
        void step1_duplicateUserCoupon_sendsRetry() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급"))
                    .given(couponIssueService).checkAlreadyIssuedUserCoupon(COUPON_ID, USER_ID, REQUEST_ID);

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep1(eq(event), any());
            verify(couponIssueService, never()).saveUserCoupon(any(), any());
        }

        @Test
        @DisplayName("step2: saveUserCoupon 실패 → sendRetryStep2 발행, step3/4 실행 안 됨")
        void step2_saveUserCouponFails_sendsRetryAndStops() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new RuntimeException("DB 저장 실패"))
                    .given(couponIssueService).saveUserCoupon(any(), any());

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep2(eq(event), any());
            verify(couponIssueService, never()).updateCouponEventLog(any(), any());
            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("step3: updateCouponEventLog 실패 → sendRetryStep3 발행, step4 실행 안 됨")
        void step3_updateEventLogFails_sendsRetryAndStops() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new RuntimeException("이벤트 로그 실패"))
                    .given(couponIssueService).updateCouponEventLog(REQUEST_ID, EventProcessingStatus.SUCCESS);

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep3(eq(event), any());
            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("step4: cosumeIssueComplete 실패 → sendRetryStep4 발행")
        void step4_publishFails_sendsRetry() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new RuntimeException("Kafka 발행 실패"))
                    .given(kafkaProducingService).cosumeIssueComplete(event);

            kafkaConsumerService.consumeFristIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep4(eq(event), any());
        }

        @Test
        @DisplayName("호출 순서: updateIssueRequestStatus(PROCESSING)이 findCoupon보다 먼저 실행됨")
        void step1_processingSetBeforeFindCoupon() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.consumeFristIssueRequest(event);

            InOrder inOrder = inOrder(couponIssueService);
            inOrder.verify(couponIssueService).updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.PROCESSING);
            inOrder.verify(couponIssueService).findCoupon(COUPON_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 메인 리스너: OPEN
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OPEN 메인 리스너")
    class OpenMainListener {

        @Test
        @DisplayName("정상 흐름: step1~4 모두 실행되고 updateIssuedQuantity는 호출 안 됨")
        void success_skipsUpdateIssuedQuantity() {
            // ⚠️ NOTE: OPEN은 수량 제한 없으므로 step3에서 updateIssuedQuantity를 skip한다
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(openCampaign);

            kafkaConsumerService.consumeOpenIssueRequest(event);

            verify(couponIssueService).saveUserCoupon(any(), any());
            verify(couponIssueService).updateCouponEventLog(REQUEST_ID, EventProcessingStatus.SUCCESS);
            verify(couponIssueService).updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.ISSUED);
            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
        }

        @Test
        @DisplayName("step2 실패 → sendRetryStep2 발행, updateIssuedQuantity 호출 안 됨")
        void step2_fails_sendsRetry_noQuantityUpdate() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(openCampaign);
            willThrow(new RuntimeException("DB 실패"))
                    .given(couponIssueService).saveUserCoupon(any(), any());

            kafkaConsumerService.consumeOpenIssueRequest(event);

            verify(kafkaProducingService).sendRetryStep2(eq(event), any());
            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 재시도 리스너
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("재시도 리스너")
    class RetryListeners {

        @Test
        @DisplayName("retryStep1: 재시도 가능 → step1~4 정상 실행")
        void retryStep1_canRetry_executesFullFlow() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(true);
            // step1(findCoupon) + step3(findCoupon) = 2회
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.retryStep1(retryEvent);

            verify(couponIssueService).saveUserCoupon(any(), any());
            verify(couponIssueService).updateIssuedQuantity(COUPON_ID);
            verify(kafkaProducingService).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("retryStep1: retryCount 초과 → allFail 발행, step 실행 안 됨")
        void retryStep1_cannotRetry_sendsAllFail() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(false);

            kafkaConsumerService.retryStep1(retryEvent);

            verify(kafkaProducingService).sendAllFail(any(), any());
            verify(couponIssueService, never()).findCoupon(anyLong());
            verify(couponIssueService, never()).saveUserCoupon(any(), any());
        }

        @Test
        @DisplayName("retryStep2: 재시도 가능 → step2~4 정상 실행")
        void retryStep2_canRetry_executesFromStep2() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(true);
            // retryStep2 body(findCoupon) + step3(findCoupon) = 2회
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.retryStep2(retryEvent);

            verify(couponIssueService).saveUserCoupon(any(), any());
            verify(couponIssueService).updateIssuedQuantity(COUPON_ID);
            verify(kafkaProducingService).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("retryStep2: retryCount 초과 → allFail 발행")
        void retryStep2_cannotRetry_sendsAllFail() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(false);

            kafkaConsumerService.retryStep2(retryEvent);

            verify(kafkaProducingService).sendAllFail(any(), any());
            verify(couponIssueService, never()).saveUserCoupon(any(), any());
        }

        @Test
        @DisplayName("retryStep3: FIRST_COME 타입 → updateIssuedQuantity 호출됨")
        void retryStep3_firstCome_updatesQuantity() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(true);
            // step3에서 findCoupon 1회 (타입 확인용)
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.retryStep3(retryEvent);

            verify(couponIssueService).updateIssuedQuantity(COUPON_ID);
        }

        @Test
        @DisplayName("retryStep3: OPEN 타입 → updateIssuedQuantity 호출 안 됨")
        void retryStep3_open_skipsQuantityUpdate() {
            // ⚠️ NOTE: campaignType이 DTO에 없어도 DB 조회(findCoupon)로 OPEN을 정확히 판별
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(true);
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(openCampaign);

            kafkaConsumerService.retryStep3(retryEvent);

            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
        }

        @Test
        @DisplayName("retryStep3: retryCount 초과 → allFail 발행, step3 실행 안 됨")
        void retryStep3_cannotRetry_sendsAllFail() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(false);

            kafkaConsumerService.retryStep3(retryEvent);

            verify(kafkaProducingService).sendAllFail(any(), any());
            verify(couponIssueService, never()).updateCouponEventLog(any(), any());
        }

        @Test
        @DisplayName("retryStep4: 재시도 가능 → step4 정상 실행")
        void retryStep4_canRetry_executesStep4() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(true);

            kafkaConsumerService.retryStep4(retryEvent);

            verify(kafkaProducingService).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("retryStep4: retryCount 초과 → allFail 발행, step4 실행 안 됨")
        void retryStep4_cannotRetry_sendsAllFail() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(false);

            kafkaConsumerService.retryStep4(retryEvent);

            verify(kafkaProducingService).sendAllFail(any(), any());
            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
        }
    }
}
