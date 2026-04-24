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
import java.util.List;

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

    @Mock
    private KafkaProducingService kafkaProducingService;

    @Mock
    private ObjectMapper objectMapper;

    private static final long COUPON_ID = 100L;
    private static final String USER_ID = "user1";
    private static final long REQUEST_ID = 1L;

    private CouponIssueEventDto event;
    private List<CouponIssueEventDto> eventBatch;
    private CouponIssueRetryEventDto retryEvent;
    private CouponCampaign firstComeCampaign;
    private CouponCampaign openCampaign;

    @BeforeEach
    void setUp() {
        event = new CouponIssueEventDto(COUPON_ID, USER_ID, REQUEST_ID);
        eventBatch = List.of(event);
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
    // 메인 리스너: FIRST_COME (배치)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FIRST_COME 메인 리스너 (배치)")
    class FirstComeMainListener {

        @Test
        @DisplayName("정상 흐름: step1~4 모두 실행되고 updateIssuedQuantity 호출됨")
        void success_updatesIssuedQuantity() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            // step2: 벌크 저장
            verify(couponIssueService).saveAllUserCouponsAndOutbox(anyList(), anyList());
            verify(couponIssueService).updateCouponEventLog(REQUEST_ID, EventProcessingStatus.SUCCESS);
            verify(couponIssueService).updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.ISSUED);
            verify(couponIssueService).updateIssuedQuantity(COUPON_ID);
            verify(kafkaProducingService).cosumeIssueComplete(event);
        }

        @Test
        @DisplayName("배치 여러 건: 검증 통과한 건만 벌크 저장됨")
        void batchMultipleEvents_onlyValidSaved() {
            CouponIssueEventDto event2 = new CouponIssueEventDto(COUPON_ID, "user2", 2L);
            CouponIssueEventDto event3 = new CouponIssueEventDto(COUPON_ID, "user3", 3L);
            List<CouponIssueEventDto> batch = List.of(event, event2, event3);

            // event1: 통과, event2: 중복으로 skip, event3: 통과
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "중복"))
                    .given(couponIssueService).checkAlreadyEvent(2L);

            kafkaConsumerService.consumeFristIssueRequest(batch);

            // 벌크 저장 1회 호출 (2건만)
            verify(couponIssueService, times(1)).saveAllUserCouponsAndOutbox(anyList(), anyList());
            // step3/4는 통과한 2건만
            verify(couponIssueService, times(2)).updateCouponEventLog(anyLong(), eq(EventProcessingStatus.SUCCESS));
            verify(kafkaProducingService, times(2)).cosumeIssueComplete(any());
            // 중복 건은 retry 발행 안 됨
            verify(kafkaProducingService, never()).sendRetryStep1(eq(event2), any());
        }

        @Test
        @DisplayName("전체 검증 실패 → saveAllUserCouponsAndOutbox 호출 안 됨")
        void allValidationFail_noSave() {
            willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "중복"))
                    .given(couponIssueService).checkAlreadyEvent(REQUEST_ID);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            verify(couponIssueService, never()).saveAllUserCouponsAndOutbox(anyList(), anyList());
        }

        @Test
        @DisplayName("step1: findCoupon 실패 → sendRetryStep1 발행, step2 이후 실행 안 됨")
        void step1_findCouponFails_sendsRetryAndStops() {
            willThrow(new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 없음"))
                    .given(couponIssueService).findCoupon(COUPON_ID);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            verify(kafkaProducingService).sendRetryStep1(eq(event), any());
            verify(couponIssueService, never()).saveAllUserCouponsAndOutbox(anyList(), anyList());
        }

        @Test
        @DisplayName("step1: checkAlreadyEvent DUPLICATED → 멱등성 보장, retry 없이 종료")
        void step1_checkAlreadyEventFails_skipsRetry() {
            willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "중복 이벤트"))
                    .given(couponIssueService).checkAlreadyEvent(REQUEST_ID);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            verify(kafkaProducingService, never()).sendRetryStep1(any(), any());
            verify(couponIssueService, never()).saveAllUserCouponsAndOutbox(anyList(), anyList());
        }

        @Test
        @DisplayName("step1: checkAlreadyIssuedUserCoupon DUPLICATED → 멱등성 보장, retry 없이 종료")
        void step1_duplicateUserCoupon_skipsRetry() {
            willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급"))
                    .given(couponIssueService).checkAlreadyIssuedUserCoupon(COUPON_ID, USER_ID, REQUEST_ID);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            verify(kafkaProducingService, never()).sendRetryStep1(any(), any());
            verify(couponIssueService, never()).saveAllUserCouponsAndOutbox(anyList(), anyList());
        }

        @Test
        @DisplayName("step2: 벌크 저장 실패 → 예외 발생")
        void step2_bulkSaveFails_throwsException() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new RuntimeException("DB 벌크 저장 실패"))
                    .given(couponIssueService).saveAllUserCouponsAndOutbox(anyList(), anyList());

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    kafkaConsumerService.consumeFristIssueRequest(eventBatch));

            verify(couponIssueService, never()).updateCouponEventLog(any(), any());
            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("step3: updateCouponEventLog 실패 → sendRetryStep3 발행, step4 실행 안 됨")
        void step3_updateEventLogFails_sendsRetryAndStops() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new RuntimeException("이벤트 로그 실패"))
                    .given(couponIssueService).updateCouponEventLog(REQUEST_ID, EventProcessingStatus.SUCCESS);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            verify(kafkaProducingService).sendRetryStep3(eq(event), any());
            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("step4: cosumeIssueComplete 실패 → sendRetryStep4 발행")
        void step4_publishFails_sendsRetry() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);
            willThrow(new RuntimeException("Kafka 발행 실패"))
                    .given(kafkaProducingService).cosumeIssueComplete(event);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            verify(kafkaProducingService).sendRetryStep4(eq(event), any());
        }

        @Test
        @DisplayName("호출 순서: updateIssueRequestStatus(PROCESSING)이 findCoupon보다 먼저 실행됨")
        void step1_processingSetBeforeFindCoupon() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.consumeFristIssueRequest(eventBatch);

            InOrder inOrder = inOrder(couponIssueService);
            inOrder.verify(couponIssueService).updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.PROCESSING);
            inOrder.verify(couponIssueService).findCoupon(COUPON_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 메인 리스너: OPEN (배치)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OPEN 메인 리스너 (배치)")
    class OpenMainListener {

        @Test
        @DisplayName("정상 흐름: step1~4 모두 실행되고 updateIssuedQuantity는 호출 안 됨")
        void success_skipsUpdateIssuedQuantity() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(openCampaign);

            kafkaConsumerService.consumeOpenIssueRequest(eventBatch);

            verify(couponIssueService).saveAllUserCouponsAndOutbox(anyList(), anyList());
            verify(couponIssueService).updateCouponEventLog(REQUEST_ID, EventProcessingStatus.SUCCESS);
            verify(couponIssueService).updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.ISSUED);
            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
        }

        @Test
        @DisplayName("step2 벌크 저장 실패 → 예외 발생, updateIssuedQuantity 호출 안 됨")
        void step2_fails_throwsException_noQuantityUpdate() {
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(openCampaign);
            willThrow(new RuntimeException("DB 실패"))
                    .given(couponIssueService).saveAllUserCouponsAndOutbox(anyList(), anyList());

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    kafkaConsumerService.consumeOpenIssueRequest(eventBatch));

            verify(couponIssueService, never()).updateIssuedQuantity(anyLong());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 재시도 리스너 (기존 단건 — 변경 없음)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("재시도 리스너")
    class RetryListeners {

        @Test
        @DisplayName("retryStep1: 재시도 가능 → step1~4 정상 실행")
        void retryStep1_canRetry_executesFullFlow() {
            given(couponIssueService.UpdateRetry(REQUEST_ID, "테스트 실패")).willReturn(true);
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
            given(couponIssueService.findCoupon(COUPON_ID)).willReturn(firstComeCampaign);

            kafkaConsumerService.retryStep3(retryEvent);

            verify(couponIssueService).updateIssuedQuantity(COUPON_ID);
        }

        @Test
        @DisplayName("retryStep3: OPEN 타입 → updateIssuedQuantity 호출 안 됨")
        void retryStep3_open_skipsQuantityUpdate() {
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