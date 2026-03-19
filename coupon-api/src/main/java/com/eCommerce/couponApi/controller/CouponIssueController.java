package com.eCommerce.couponApi.controller;

import com.eCommerce.couponApi.dto.CouponIssueKafkaDto;
import com.eCommerce.couponApi.dto.CouponIssueRequestDto;
import com.eCommerce.couponApi.dto.CouponIssueResponseDto;
import com.eCommerce.couponApi.dto.CouponIssueResultDto;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;
import com.eCommerce.couponApi.service.CouponEventProducer;
import com.eCommerce.couponApi.service.CouponRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

    private final CouponRedisService couponRedisService;
    private final CouponEventProducer couponEventProducer;

    @PostMapping("/issue")
    public Mono<CouponIssueResponseDto> couponIssue(@RequestBody CouponIssueRequestDto dto) {
        return couponRedisService.issue(dto.couponId(), dto.userId())
                .filter(result -> result.code() == CouponIssueReqeustCode.SUCCESS)
                .flatMap(result -> {
                    couponEventProducer.publishIssuedRequest(new CouponIssueKafkaDto(dto.couponId(), dto.userId(), result.requestId()));
                    return Mono.just(new CouponIssueResponseDto(result.code().name(), "성공"));
                });
    }


}
