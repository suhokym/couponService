package com.eCommerce.couponConsumer.config;

import com.eCommerce.couponConsumer.service.KafkaProducingService;
import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @InjectMocks
    private OutboxScheduler outboxScheduler;

    @Mock
    private CouponIssueOutboxService outboxService;

    @Mock
    private KafkaProducingService kafkaProducingService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // 테스트용 유효한 payload JSON
    private String validPayload;

    @BeforeEach
    void setUp() throws Exception {
        CouponIssueEventDto dto = new CouponIssueEventDto(1L, "user1", 10L);
        validPayload = objectMapper.writeValueAsString(dto);
    }

    private OutboxEvent buildOutbox(Long outboxId, String payload) {
        return OutboxEvent.builder()
                .outboxId(outboxId)
                .aggregateType("CouponIssueRequest")
                .aggregateId(outboxId)
                .eventType("SAVE_USER_COUPON")
                .payload(payload)
                .publishStatus(OutboxPublishStatus.PENDING)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // publishPendingOutbox()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("publishPendingOutbox()")
    class PublishPendingOutbox {

        @Test
        @DisplayName("PENDING 이벤트 2건 — Kafka 발행 2회 + markPublished 2회")
        void twoEvents_publishesBothAndMarksPublished() {
            OutboxEvent e1 = buildOutbox(1L, validPayload);
            OutboxEvent e2 = buildOutbox(2L, validPayload);
            given(outboxService.findPendingOutbox()).willReturn(List.of(e1, e2));

            outboxScheduler.publishPendingOutbox();

            verify(kafkaProducingService, times(2)).cosumeIssueComplete(any(CouponIssueEventDto.class));
            verify(outboxService, times(2)).markPublished(anyLong());
            verify(outboxService, never()).markFailed(anyLong());
        }

        @Test
        @DisplayName("PENDING 이벤트 없음 — Kafka 발행 안 됨")
        void noEvents_noKafkaPublish() {
            given(outboxService.findPendingOutbox()).willReturn(List.of());

            outboxScheduler.publishPendingOutbox();

            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
        }

        @Test
        @DisplayName("Kafka 발행 실패 → markFailed 호출, markPublished 호출 안 됨")
        void kafkaFail_marksFailedNotPublished() {
            OutboxEvent event = buildOutbox(1L, validPayload);
            given(outboxService.findPendingOutbox()).willReturn(List.of(event));
            willThrow(new RuntimeException("Kafka 연결 실패"))
                    .given(kafkaProducingService).cosumeIssueComplete(any());

            outboxScheduler.publishPendingOutbox();

            verify(outboxService).markFailed(1L);
            verify(outboxService, never()).markPublished(anyLong());
        }

        @Test
        @DisplayName("payload JSON 역직렬화 실패 → markFailed 호출")
        void deserializationFail_marksFailedAndContinues() {
            OutboxEvent badEvent = buildOutbox(1L, "invalid-json");
            OutboxEvent goodEvent = buildOutbox(2L, validPayload);
            given(outboxService.findPendingOutbox()).willReturn(List.of(badEvent, goodEvent));

            outboxScheduler.publishPendingOutbox();

            // bad 이벤트: markFailed / good 이벤트: markPublished
            verify(outboxService).markFailed(1L);
            verify(outboxService).markPublished(2L);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // retryFailedOutbox()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("retryFailedOutbox()")
    class RetryFailedOutbox {

        @Test
        @DisplayName("재시도 가능 이벤트 1건 — Kafka 재발행 + markPublished")
        void oneRetryable_publishesAndMarksPublished() {
            OutboxEvent event = buildOutbox(5L, validPayload);
            given(outboxService.findRetryableFailedOutbox()).willReturn(List.of(event));

            outboxScheduler.retryFailedOutbox();

            verify(kafkaProducingService).cosumeIssueComplete(any(CouponIssueEventDto.class));
            verify(outboxService).markPublished(5L);
            verify(outboxService, never()).markFailed(anyLong());
        }

        @Test
        @DisplayName("재시도 중 Kafka 실패 → markFailed 호출")
        void retryKafkaFail_marksFailedAgain() {
            OutboxEvent event = buildOutbox(5L, validPayload);
            given(outboxService.findRetryableFailedOutbox()).willReturn(List.of(event));
            willThrow(new RuntimeException("Kafka 재발행 실패"))
                    .given(kafkaProducingService).cosumeIssueComplete(any());

            outboxScheduler.retryFailedOutbox();

            verify(outboxService).markFailed(5L);
            verify(outboxService, never()).markPublished(anyLong());
        }

        @Test
        @DisplayName("재시도 가능 이벤트 없음 — 아무것도 호출 안 됨")
        void noRetryable_nothingCalled() {
            given(outboxService.findRetryableFailedOutbox()).willReturn(List.of());

            outboxScheduler.retryFailedOutbox();

            verify(kafkaProducingService, never()).cosumeIssueComplete(any());
            verify(outboxService, never()).markPublished(anyLong());
            verify(outboxService, never()).markFailed(anyLong());
        }
    }
}
