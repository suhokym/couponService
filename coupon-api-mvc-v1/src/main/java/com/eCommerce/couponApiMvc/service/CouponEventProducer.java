package com.eCommerce.couponApiMvc.service;

import com.eCommerce.couponApiMvc.dto.CouponIssueKafkaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class CouponEventProducer {

    private final KafkaTemplate<String, CouponIssueKafkaDto> kafkaTemplate;

    // ⚠️ NOTE: MVC에서는 KafkaTemplate.send()가 비동기(ListenableFuture)로 동작 — fire-and-forget
    //          발행 실패 시 예외 로그를 남기고 클라이언트에게는 성공 응답 반환
    //          (WebFlux 버전과 달리 체인 에러 전파 불필요)
    public void publishIssuedRequest(CouponIssueKafkaDto event) {
        kafkaTemplate.send("coupon-issue-requested", String.valueOf(event.couponId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 이벤트 발행 실패 couponId={}, userId={}: {}",
                                event.couponId(), event.userId(), ex.getMessage());
                    }
                });
    }
}
