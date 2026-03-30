package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private OutboxEvent outboxEvent;

    @BeforeEach
    void setUp() {
        outboxEvent = OutboxEvent.builder()
                .aggregateType("CouponIssueRequest")
                .aggregateId(1L)
                .eventType("SAVE_USER_COUPON")
                .payload("{\"couponId\":1,\"userId\":\"user1\"}")
                .publishStatus(OutboxPublishStatus.PENDING)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // markPublished()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markPublished()")
    class MarkPublished {

        @Test
        @DisplayName("PENDING → PUBLISHED 상태 변경")
        void pendingToPublished() {
            outboxEvent.markPublished();

            assertThat(outboxEvent.getPublishStatus()).isEqualTo(OutboxPublishStatus.PUBLISHED);
        }

        @Test
        @DisplayName("markPublished() 후 retryCount는 변화 없음")
        void retryCountUnchangedAfterPublished() {
            int before = outboxEvent.getRetryCount();
            outboxEvent.markPublished();

            assertThat(outboxEvent.getRetryCount()).isEqualTo(before);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // markFailed()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("PENDING → FAILED 상태 변경")
        void pendingToFailed() {
            outboxEvent.markFailed();

            assertThat(outboxEvent.getPublishStatus()).isEqualTo(OutboxPublishStatus.FAILED);
        }

        @Test
        @DisplayName("최초 실패: retryCount 0 → 1")
        void firstFail_retryCountIncremented() {
            outboxEvent.markFailed();

            assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("연속 3회 실패: retryCount = 3")
        void threeFails_retryCountIsThree() {
            outboxEvent.markFailed();
            outboxEvent.markFailed();
            outboxEvent.markFailed();

            assertThat(outboxEvent.getRetryCount()).isEqualTo(3);
        }
    }
}
