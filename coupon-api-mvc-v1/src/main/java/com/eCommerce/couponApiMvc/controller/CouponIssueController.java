package com.eCommerce.couponApiMvc.controller;

import com.eCommerce.couponApiMvc.dto.CouponIssueKafkaDto;
import com.eCommerce.couponApiMvc.dto.CouponIssueRequestDto;
import com.eCommerce.couponApiMvc.dto.CouponIssueResponseDto;
import com.eCommerce.couponApiMvc.dto.CouponIssueResultDto;
import com.eCommerce.couponApiMvc.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponApiMvc.service.CouponEventProducer;
import com.eCommerce.couponApiMvc.service.CouponRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

    private final CouponRedisService couponRedisService;
    private final CouponEventProducer couponEventProducer;

    @PostMapping("/issue")
    public CouponIssueResponseDto couponIssue(@RequestBody CouponIssueRequestDto dto) {
        log.info("Coupon Issue Request: {}", dto);

        CouponIssueResultDto result = couponRedisService.issue(dto.couponId(), dto.userId());

        if (result.code() == CouponIssueRequestCode.SUCCESS) {
            couponEventProducer.publishIssuedRequest(
                    new CouponIssueKafkaDto(dto.couponId(), dto.userId(), result.requestId()));
            return new CouponIssueResponseDto(result.code().name(), "성공");
        }

        return new CouponIssueResponseDto(result.code().name(), "발급 불가");
    }
}
