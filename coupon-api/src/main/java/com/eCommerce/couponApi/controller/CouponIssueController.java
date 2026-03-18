package com.eCommerce.couponApi.controller;

import com.eCommerce.couponApi.dto.CouponIssueReqeustDto;
import com.eCommerce.couponApi.dto.CouponIssueResponseDto;
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
    public Mono<CouponIssueResponseDto> couponIssue(@RequestBody CouponIssueReqeustDto dto) {
        return couponRedisService.issue(dto.couponId(), dto.userId())
                .filter(code -> code == CouponIssueReqeustCode.SUCCESS)
                .flatMap(code -> {
                    couponEventProducer.publishIssuedRequest(dto);
                    return Mono.just(new CouponIssueResponseDto(code.name(), "성공"));
                });
    }


}
