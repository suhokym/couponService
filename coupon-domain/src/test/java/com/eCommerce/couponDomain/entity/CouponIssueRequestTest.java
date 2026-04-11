package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueRequestTest {

    private CouponCampaign campaign;
    private CouponIssueRequest issueRequest;

    @BeforeEach
    void setUp() {
        campaign = CouponCampaign.builder()
                .name("테스트 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .IssuedQuantity(0)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        issueRequest = CouponIssueRequest.builder()
                .userId("user1")
                .campaign(campaign)
                .status(IssueRequestStatus.REQUESTED)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // updateStatus()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("REQUESTED → PROCESSING 상태 변경")
        void requestedToProcessing() {
            issueRequest.updateStatus(IssueRequestStatus.PROCESSING);

            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.PROCESSING);
        }

        @Test
        @DisplayName("PROCESSING → ISSUED 상태 변경")
        void processingToIssued() {
            issueRequest.updateStatus(IssueRequestStatus.PROCESSING);
            issueRequest.updateStatus(IssueRequestStatus.ISSUED);

            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.ISSUED);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateRetryCount()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateRetryCount()")
    class UpdateRetryCount {

        @Test
        @DisplayName("최초 재시도: retryCount 0 → 1, 상태 RETRYING")
        void firstRetry_incrementsCountAndSetsRetrying() {
            issueRequest.updateRetryCount("오류 발생");

            assertThat(issueRequest.getRetryCount()).isEqualTo(1);
            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.RETRYING);
        }

        @Test
        @DisplayName("연속 3회 재시도: retryCount = 3")
        void threeRetries_countIsThree() {
            issueRequest.updateRetryCount("오류1");
            issueRequest.updateRetryCount("오류2");
            issueRequest.updateRetryCount("오류3");

            assertThat(issueRequest.getRetryCount()).isEqualTo(3);
            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.RETRYING);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateAllFail()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateAllFail()")
    class UpdateAllFail {

        @Test
        @DisplayName("failReason 저장 + 상태 FAILED_FATAL 변경")
        void setsFailReasonAndFatalStatus() {
            issueRequest.updateAllFail("DB 오류로 최종 실패");

            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.FAILED_FATAL);
            assertThat(issueRequest.getFailReason()).isEqualTo("DB 오류로 최종 실패");
        }

        @Test
        @DisplayName("빈 문자열 failReason도 저장됨")
        void emptyFailReason_isSaved() {
            issueRequest.updateAllFail("");

            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.FAILED_FATAL);
            assertThat(issueRequest.getFailReason()).isEqualTo("");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // faliedBusiness()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("faliedBusiness()")
    class FailedBusiness {

        @Test
        @DisplayName("비즈니스 실패: failReason 저장 + 상태 FAILED_FATAL")
        void setsFailReasonAndFatalStatus() {
            issueRequest.faliedBusiness("이미 발급된 쿠폰입니다.");

            assertThat(issueRequest.getStatus()).isEqualTo(IssueRequestStatus.FAILED_FATAL);
            assertThat(issueRequest.getFailReason()).isEqualTo("이미 발급된 쿠폰입니다.");
        }
    }
}
