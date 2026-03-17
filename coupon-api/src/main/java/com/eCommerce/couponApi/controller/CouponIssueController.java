package com.eCommerce.couponApi.controller;

import com.eCommerce.couponApi.dto.CouponIssueReqeustDto;
import com.eCommerce.couponApi.dto.CouponIssueResponseDto;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;
import com.eCommerce.couponApi.service.CouponRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

    private final CouponRedisService couponRedisService;

    @PostMapping("/issue")
    public Mono<CouponIssueResponseDto> couponIssue(@RequestBody CouponIssueReqeustDto dto) {
        return couponRedisService.issue(dto.couponId(), dto.userId())
                .map(code -> new CouponIssueResponseDto(code.name(), "발급 요청 완료"));
    }

}
