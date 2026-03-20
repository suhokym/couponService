package com.eCommerce.couponConsumer.config;

import com.eCommerce.couponConsumer.service.KafkaProducingService;
import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CouponIssueErrorHandler {

    private final CouponIssueService couponIssueService;

    private final KafkaProducingService kafkaProducingService;


    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, CouponIssueEventDto> kafkaTemplate) {
        // 실패한 메시지를 DLT로 보냄
            DeadLetterPublishingRecoverer recoverer =
                    new DeadLetterPublishingRecoverer(kafkaTemplate) {
                        @Override
                        public void accept(ConsumerRecord<?, ?> record, Exception exception) {
                            // 3번 다 실패했을 때 실행할 로직
                            log.error("최종 실패 → DLT 이동 - key: {}, error: {}",
                                    record.key(), exception.getMessage());

                            // 여기서 원하는 로직 실행
                            // 예: FAILED_FATAL 상태 저장, 알림 등
                            CouponIssueEventDto event = (CouponIssueEventDto) record.value();
                            couponIssueService.allFailed(exception.getMessage(),event.couponIssueRequestId());
                            kafkaProducingService.cosumeIssueAllFailed();
                            // DLT로 전송
                            super.accept(record, exception);
                        }
                    };
        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        defaultErrorHandler.addNotRetryableExceptions(CouponIssueException.class);

        defaultErrorHandler.setRetryListeners(((record, ex, deliveryAttempt) -> {
            log.warn("재시도 {}/3 - topic: {}, key: {}, error: {}",
                    deliveryAttempt, record.topic(), record.key(), ex.getMessage());
            //재시도 시 로직
            // 1. IssueRequest 재시도 로직 실행
            // 2. eventLog 재시도중 으로 변경
            CouponIssueEventDto event = (CouponIssueEventDto) record.value();
            couponIssueService.UpdateRetry(event.couponIssueRequestId(), deliveryAttempt);


        }));

        // 3번 재시도, 1초 간격, 다 실패하면 DLT로
        return defaultErrorHandler;
    }

}
