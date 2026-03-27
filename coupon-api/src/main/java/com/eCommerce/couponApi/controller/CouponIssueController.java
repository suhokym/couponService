package com.eCommerce.couponApi.controller;

import com.eCommerce.couponApi.dto.CouponIssueKafkaDto;
import com.eCommerce.couponApi.dto.CouponIssueRequestDto;
import com.eCommerce.couponApi.dto.CouponIssueResponseDto;
import com.eCommerce.couponApi.dto.CouponIssueResultDto;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponApi.service.CouponEventProducer;
import com.eCommerce.couponApi.service.CouponRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

    private final CouponRedisService couponRedisService;
    private final CouponEventProducer couponEventProducer;

    @PostMapping("/issue")
    public Mono<CouponIssueResponseDto> couponIssue(@RequestBody CouponIssueRequestDto dto) {
        log.info("Coupon Issue Request: {}", dto);
        return couponRedisService.issueCoupon(dto.couponId(), dto.userId())
                // ⑤ 서비스 반환 단계 — 서비스 레이어 전체 실패 식별
                .checkpoint("쿠폰 발급 서비스 처리 완료")
                .flatMap(result -> {
                    if (result.code() == CouponIssueRequestCode.SUCCESS_FIRST_COME) {
                        // ⚠️ NOTE: Kafka 발행을 flatMap으로 리액티브 체인에 포함 — 발행 실패 시
                        //          에러 signal이 클라이언트까지 전파되어 데이터 일관성 보장
                        return couponEventProducer.publishFirstCouponIssuedRequest(new CouponIssueKafkaDto(dto.couponId(), dto.userId(), result.requestId()))
                                // ⑥ Kafka 발행 단계 — KafkaTemplate 발행 오류 식별
                                .checkpoint("First-Come Kafka 이벤트 발행 완료")
                                .thenReturn(new CouponIssueResponseDto(result.code().name(), "성공"));
                    }else if (result.code() == CouponIssueRequestCode.SUCCESS_OPEN) {
                        return couponEventProducer.publishOpenCouponIssuedRequest(new CouponIssueKafkaDto(dto.couponId(), dto.userId(), result.requestId()))
                                .checkpoint("Open Kafka 이벤트 발행 완료")
                                .thenReturn(new CouponIssueResponseDto(result.code().name(), "성공"));
                    }
                    return Mono.just(new CouponIssueResponseDto(result.code().name(), "발급 불가"));
                });
    }


}
