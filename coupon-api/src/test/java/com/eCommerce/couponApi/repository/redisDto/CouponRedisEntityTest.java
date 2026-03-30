package com.eCommerce.couponApi.repository.redisDto;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CouponRedisEntityTest {

    private CouponRedisEntity build(CampaignStatus status,
                                    LocalDateTime startAt,
                                    LocalDateTime endAt) {
        return new CouponRedisEntity(
                1L, CampaignType.FIRST_COME, 100, startAt, endAt, status
        );
    }

    // ══════════════════════════════════════════════════════════════
    // availableIssueableCoupon()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("availableIssueableCoupon() — 발급 가능 검증")
    class AvailableIssueableCoupon {

        @Test
        @DisplayName("ACTIVE + 기간 유효 — 예외 없음")
        void active_valid_noException() {
            CouponRedisEntity entity = build(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1));

            assertThatCode(entity::availableIssueableCoupon).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ENDED 상태 → FAIL_COUPON_ISSUE_REQUEST 예외")
        void ended_throwsFail() {
            CouponRedisEntity entity = build(
                    CampaignStatus.ENDED,
                    LocalDateTime.now().minusDays(10),
                    LocalDateTime.now().minusDays(1));

            assertThatThrownBy(entity::availableIssueableCoupon)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("INACTIVE 상태 → FAIL_COUPON_ISSUE_REQUEST 예외")
        void inactive_throwsFail() {
            CouponRedisEntity entity = build(
                    CampaignStatus.INACTIVE,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1));

            assertThatThrownBy(entity::availableIssueableCoupon)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("ACTIVE 이지만 아직 시작 전 → INVALID_COUPON_ISSUE_DATE 예외")
        void activeButNotStarted_throwsInvalidDate() {
            CouponRedisEntity entity = build(
                    CampaignStatus.ACTIVE,
                    LocalDateTime.now().plusHours(1),
                    LocalDateTime.now().plusDays(1));

            assertThatThrownBy(entity::availableIssueableCoupon)
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_COUPON_ISSUE_DATE);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CouponIssueRequestCode.checkRequestResult()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CouponIssueRequestCode.checkRequestResult()")
    class CheckRequestResult {

        @Test
        @DisplayName("SUCCESS_FIRST_COME — 예외 없음")
        void successFirstCome_noException() {
            assertThatCode(() -> CouponIssueRequestCode.checkRequestResult(CouponIssueRequestCode.SUCCESS_FIRST_COME))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DUPLICATE_COUPON_ISSUE → DUPLICATED_COUPON_ISSUE 예외")
        void duplicate_throwsDuplicated() {
            assertThatThrownBy(() -> CouponIssueRequestCode.checkRequestResult(CouponIssueRequestCode.DUPLICATE_COUPON_ISSUE))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATED_COUPON_ISSUE);
        }

        @Test
        @DisplayName("INVALID_COUPON_ISSUE_QUANTITY → INVALID_COUPON_ISSUE_QUANTITY 예외")
        void soldOut_throwsInvalidQuantity() {
            assertThatThrownBy(() -> CouponIssueRequestCode.checkRequestResult(CouponIssueRequestCode.INVALID_COUPON_ISSUE_QUANTITY))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_COUPON_ISSUE_QUANTITY);
        }

        @Test
        @DisplayName("FAIL — FAIL_COUPON_ISSUE_REQUEST 예외")
        void fail_throwsFail() {
            assertThatThrownBy(() -> CouponIssueRequestCode.checkRequestResult(CouponIssueRequestCode.FAIL))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }
    }
}
