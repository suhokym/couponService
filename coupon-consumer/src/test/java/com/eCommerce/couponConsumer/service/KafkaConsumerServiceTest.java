package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private CouponIssueEventDto event;
    private CouponCampaign campaign;

    @BeforeEach
    void setUp() {
        event = new CouponIssueEventDto(100L, "user1", 1L);
        campaign = CouponCampaign.builder()
                .name("테스트 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Test
    @DisplayName("정상 흐름: saveUserCoupon, updateCouponEventLog(SUCCESS), updateIssueRequestStatus(ISSUED) 각 1회 호출")
    void consumeFristIssueRequest_success() {
        // given
        given(couponIssueService.findCoupon(100L)).willReturn(campaign);

        // when
        kafkaConsumerService.consumeFristIssueRequest(event);

        // then
        verify(couponIssueService).saveUserCoupon(any());
        verify(couponIssueService).updateCouponEventLog(1L, EventProcessingStatus.SUCCESS);
        verify(couponIssueService).updateIssueRequestStatus(1L, IssueRequestStatus.ISSUED);
    }

    @Test
    @DisplayName("findCoupon 예외 시: 예외 전파, saveUserCoupon never 호출")
    void consumeFristIssueRequest_couponNotFound() {
        // given
        willThrow(new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "존재하지 않는 쿠폰"))
                .given(couponIssueService).findCoupon(100L);

        // when & then
        assertThrows(CouponIssueException.class,
                () -> kafkaConsumerService.consumeFristIssueRequest(event));

        verify(couponIssueService, never()).saveUserCoupon(any());
    }

    @Test
    @DisplayName("checkAlreadyEvent 예외 시: 예외 전파, saveUserCoupon never 호출")
    void consumeFristIssueRequest_alreadyEvent() {
        // given
        given(couponIssueService.findCoupon(100L)).willReturn(campaign);
        willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "이미 처리된 이벤트"))
                .given(couponIssueService).checkAlreadyEvent(1L);

        // when & then
        assertThrows(CouponIssueException.class,
                () -> kafkaConsumerService.consumeFristIssueRequest(event));

        verify(couponIssueService, never()).saveUserCoupon(any());
    }

    @Test
    @DisplayName("checkAlreadyIssuedUserCoupon 예외 시: 예외 전파, saveUserCoupon never 호출")
    void consumeFristIssueRequest_duplicateUserCoupon() {
        // given
        given(couponIssueService.findCoupon(100L)).willReturn(campaign);
        willThrow(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급된 유저"))
                .given(couponIssueService).checkAlreadyIssuedUserCoupon(100L, "user1", 1L);

        // when & then
        assertThrows(CouponIssueException.class,
                () -> kafkaConsumerService.consumeFristIssueRequest(event));

        verify(couponIssueService, never()).saveUserCoupon(any());
    }

    @Test
    @DisplayName("saveUserCoupon 실패 시: 예외 흡수, updateCouponEventLog / updateIssueRequestStatus(ISSUED) never 호출")
    void consumeFristIssueRequest_saveUserCouponFails() {
        // given
        given(couponIssueService.findCoupon(100L)).willReturn(campaign);
        willThrow(new RuntimeException("DB 저장 실패"))
                .given(couponIssueService).saveUserCoupon(any());

        // when (catch 블록이 예외를 흡수하므로 예외가 전파되지 않음)
        kafkaConsumerService.consumeFristIssueRequest(event);

        // then
        verify(couponIssueService, never()).updateCouponEventLog(any(), any());
        verify(couponIssueService, never()).updateIssueRequestStatus(eq(1L), eq(IssueRequestStatus.ISSUED));
    }

    @Test
    @DisplayName("호출 순서 검증: updateIssueRequestStatus(PROCESSING)이 findCoupon보다 먼저 호출")
    void consumeFristIssueRequest_processingStatusUpdatedFirst() {
        // given
        given(couponIssueService.findCoupon(100L)).willReturn(campaign);

        // when
        kafkaConsumerService.consumeFristIssueRequest(event);

        // then
        InOrder inOrder = inOrder(couponIssueService);
        inOrder.verify(couponIssueService).updateIssueRequestStatus(1L, IssueRequestStatus.PROCESSING);
        inOrder.verify(couponIssueService).findCoupon(100L);
    }

}
