package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CouponCampaignTest {

    // 기본 ACTIVE 캠페인 빌더 헬퍼
    private CouponCampaign buildCampaign(CampaignStatus status,
                                         LocalDateTime startAt,
                                         LocalDateTime endAt,
                                         Integer totalQuantity,
                                         Integer issuedQuantity) {
        return CouponCampaign.builder()
                .name("테스트 쿠폰")
                .type(CampaignType.FIRST_COME)
                .status(status)
                .startAt(startAt)
                .endAt(endAt)
                .totalQuantity(totalQuantity)
                .IssuedQuantity(issuedQuantity)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // validateIssuable()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateIssuable() — 발급 가능 검증")
    class ValidateIssuable {

        @Test
        @DisplayName("정상 ACTIVE 캠페인 (기간·수량 모두 통과) — 예외 없음")
        void active_withinDate_belowQuantity_noException() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 50);

            assertThatCode(campaign::validateIssuable).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("INACTIVE 상태 → FAIL_COUPON_ISSUE_REQUEST 예외")
        void inactive_throwsFail() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.INACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 0);

            assertThatThrownBy(campaign::validateIssuable)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("ENDED 상태 → FAIL_COUPON_ISSUE_REQUEST 예외")
        void ended_throwsFail() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ENDED,
                    LocalDateTime.now().minusDays(10),
                    LocalDateTime.now().minusDays(1),
                    100, 100);

            assertThatThrownBy(campaign::validateIssuable)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("아직 시작 전 캠페인 → INVALID_COUPON_ISSUE_DATE 예외")
        void notYetStarted_throwsInvalidDate() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(10),
                    100, 0);

            assertThatThrownBy(campaign::validateIssuable)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_COUPON_ISSUE_DATE);
        }

        @Test
        @DisplayName("이미 종료된 기간 캠페인 → INVALID_COUPON_ISSUE_DATE 예외")
        void expired_throwsInvalidDate() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(10),
                    LocalDateTime.now().minusDays(1),
                    100, 50);

            assertThatThrownBy(campaign::validateIssuable)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_COUPON_ISSUE_DATE);
        }

        @Test
        @DisplayName("발급 수량 = 총 수량 (매진) → INVALID_COUPON_ISSUE_QUANTITY 예외")
        void soldOut_throwsInvalidQuantity() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 100);

            assertThatThrownBy(campaign::validateIssuable)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_COUPON_ISSUE_QUANTITY);
        }

        @Test
        @DisplayName("발급 수량 > 총 수량 → INVALID_COUPON_ISSUE_QUANTITY 예외")
        void overSoldOut_throwsInvalidQuantity() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 101);

            assertThatThrownBy(campaign::validateIssuable)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_COUPON_ISSUE_QUANTITY);
        }

        @Test
        @DisplayName("totalQuantity null (OPEN 무제한) — 수량 검증 skip, 예외 없음")
        void nullTotalQuantity_noQuantityCheck_noException() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    null, null);

            assertThatCode(campaign::validateIssuable).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("마지막 수량 (issuedQuantity = totalQuantity - 1) — 예외 없음")
        void lastOne_noException() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 99);

            assertThatCode(campaign::validateIssuable).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateStatus() / updateIssuedQuantity()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("상태·수량 업데이트")
    class Updates {

        @Test
        @DisplayName("updateStatus() — 상태가 파라미터 값으로 변경됨")
        void updateStatus_changesField() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 0);

            campaign.updateStatus(CampaignStatus.ENDED);

            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ENDED);
        }

        @Test
        @DisplayName("updateIssuedQuantity() — 발급 수량 1 증가")
        void updateIssuedQuantity_incrementsByOne() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 49);

            campaign.updateIssuedQuantity();

            assertThat(campaign.getIssuedQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("updateIssuedQuantity() 연속 2회 — 2 증가")
        void updateIssuedQuantity_twice_incrementsByTwo() {
            CouponCampaign campaign = buildCampaign(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    100, 10);

            campaign.updateIssuedQuantity();
            campaign.updateIssuedQuantity();

            assertThat(campaign.getIssuedQuantity()).isEqualTo(12);
        }
    }
}
