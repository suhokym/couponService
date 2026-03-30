package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponEventLogTest {

    private CouponEventLog eventLog;

    @BeforeEach
    void setUp() {
        CouponCampaign campaign = CouponCampaign.builder()
                .name("테스트 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .IssuedQuantity(0)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        CouponIssueRequest request = CouponIssueRequest.builder()
                .userId("user1")
                .campaign(campaign)
                .status(IssueRequestStatus.PROCESSING)
                .build();

        eventLog = CouponEventLog.builder()
                .request(request)
                .eventType("first-coupon-issue-requested")
                .payload("{\"couponId\":1}")
                .processingStatus(EventProcessingStatus.PROGRESS)
                .build();
    }

    @Test
    @DisplayName("updateStatus() — 상태가 지정한 값으로 변경됨")
    void updateStatus_changesProcessingStatus() {
        eventLog.updateStatus(EventProcessingStatus.SUCCESS);

        assertThat(eventLog.getProcessingStatus()).isEqualTo(EventProcessingStatus.SUCCESS);
    }

    @Test
    @DisplayName("updateRetryStatus() — 상태가 RETRYING으로 변경됨")
    void updateRetryStatus_setsRetrying() {
        eventLog.updateRetryStatus();

        assertThat(eventLog.getProcessingStatus()).isEqualTo(EventProcessingStatus.RETRYING);
    }

    @Test
    @DisplayName("updatefailedStatus() — 상태가 FAILED로 변경됨")
    void updateFailedStatus_setsFailed() {
        eventLog.updatefailedStatus();

        assertThat(eventLog.getProcessingStatus()).isEqualTo(EventProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("PROGRESS → SUCCESS 순서로 변경 가능")
    void progressThenSuccess() {
        assertThat(eventLog.getProcessingStatus()).isEqualTo(EventProcessingStatus.PROGRESS);

        eventLog.updateStatus(EventProcessingStatus.SUCCESS);

        assertThat(eventLog.getProcessingStatus()).isEqualTo(EventProcessingStatus.SUCCESS);
    }
}
