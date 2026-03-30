package com.eCommerce.couponDomain.service;

import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CouponIssueOutboxServiceTest {

    @InjectMocks
    private CouponIssueOutboxService outboxService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEvent buildOutbox(OutboxPublishStatus status, int retryCount) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("CouponIssueRequest")
                .aggregateId(1L)
                .eventType("SAVE_USER_COUPON")
                .payload("{}")
                .publishStatus(status)
                .build();
        for (int i = 0; i < retryCount; i++) {
            event.markFailed();
        }
        return event;
    }

    // ══════════════════════════════════════════════════════════════
    // findPendingOutbox()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findPendingOutbox()")
    class FindPendingOutbox {

        @Test
        @DisplayName("PENDING 이벤트 목록 반환")
        void returnsPendingEvents() {
            OutboxEvent e1 = buildOutbox(OutboxPublishStatus.PENDING, 0);
            OutboxEvent e2 = buildOutbox(OutboxPublishStatus.PENDING, 0);
            given(outboxEventRepository.findByPublishStatus(OutboxPublishStatus.PENDING))
                    .willReturn(List.of(e1, e2));

            List<OutboxEvent> result = outboxService.findPendingOutbox();

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(e -> e.getPublishStatus() == OutboxPublishStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING 이벤트 없음 — 빈 리스트 반환")
        void noEvents_returnsEmpty() {
            given(outboxEventRepository.findByPublishStatus(OutboxPublishStatus.PENDING))
                    .willReturn(List.of());

            List<OutboxEvent> result = outboxService.findPendingOutbox();

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // findRetryableFailedOutbox()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findRetryableFailedOutbox()")
    class FindRetryableFailedOutbox {

        @Test
        @DisplayName("retryCount < 3 인 FAILED 이벤트 반환")
        void returnsRetryableEvents() {
            OutboxEvent e1 = buildOutbox(OutboxPublishStatus.FAILED, 1);
            OutboxEvent e2 = buildOutbox(OutboxPublishStatus.FAILED, 2);
            given(outboxEventRepository.findByPublishStatusAndRetryCountLessThan(OutboxPublishStatus.FAILED, 3))
                    .willReturn(List.of(e1, e2));

            List<OutboxEvent> result = outboxService.findRetryableFailedOutbox();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("재시도 가능한 이벤트 없음 — 빈 리스트 반환")
        void noRetryableEvents_returnsEmpty() {
            given(outboxEventRepository.findByPublishStatusAndRetryCountLessThan(OutboxPublishStatus.FAILED, 3))
                    .willReturn(List.of());

            List<OutboxEvent> result = outboxService.findRetryableFailedOutbox();

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // markPublished()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markPublished()")
    class MarkPublished {

        @Test
        @DisplayName("존재하는 outbox — PUBLISHED 상태로 변경")
        void found_marksPublished() {
            OutboxEvent event = buildOutbox(OutboxPublishStatus.PENDING, 0);
            given(outboxEventRepository.findById(1L)).willReturn(Optional.of(event));

            outboxService.markPublished(1L);

            assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.PUBLISHED);
        }

        @Test
        @DisplayName("존재하지 않는 outboxId → OUTBOX_NOT_EXIST 예외")
        void notFound_throwsOutboxNotExist() {
            given(outboxEventRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> outboxService.markPublished(99L))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OUTBOX_NOT_EXIST);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // markFailed()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("존재하는 outbox — FAILED 상태 + retryCount 증가")
        void found_marksFailedAndIncrements() {
            OutboxEvent event = buildOutbox(OutboxPublishStatus.PENDING, 0);
            given(outboxEventRepository.findById(1L)).willReturn(Optional.of(event));

            outboxService.markFailed(1L);

            assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.FAILED);
            assertThat(event.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 outboxId → OUTBOX_NOT_EXIST 예외")
        void notFound_throwsOutboxNotExist() {
            given(outboxEventRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> outboxService.markFailed(99L))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OUTBOX_NOT_EXIST);
        }
    }
}
