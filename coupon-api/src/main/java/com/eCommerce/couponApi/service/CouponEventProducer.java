package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.dto.CouponIssueKafkaDto;
import com.eCommerce.couponApi.dto.CouponIssueRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class CouponEventProducer {

    private final KafkaTemplate<String, CouponIssueKafkaDto> kafkaTemplate;

    // ⚠️ NOTE: Mono<Void> 반환 — KafkaTemplate.send()의 CompletableFuture를 Mono로 래핑해
    //          리액티브 체인에 포함시킴. 발행 실패 시 에러 signal이 체인으로 전파되어
    //          클라이언트에게 실패 응답을 보장함 (silent swallow 방지)
    // ⚠️ NOTE: 파티션 키를 couponId → userId로 변경
    //          couponId 키 사용 시 동일 쿠폰 모든 요청이 파티션 1개로 집중되어
    //          consumer 스레드 1개가 순차 처리 → REQUESTED 적체 발생 (Bug4)
    public Mono<Void> publishFirstCouponIssuedRequest(CouponIssueKafkaDto event) {
        return Mono.fromFuture(
                kafkaTemplate.send("first-coupon-issue-requested", event.userId(), event)
        ).then();
    }

    public Mono<Void> publishOpenCouponIssuedRequest(CouponIssueKafkaDto event) {
        return Mono.fromFuture(
                kafkaTemplate.send("open-coupon-issue-requested", event.userId(), event)
        ).then();
    }




}
