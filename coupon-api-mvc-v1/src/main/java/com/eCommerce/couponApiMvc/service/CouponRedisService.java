package com.eCommerce.couponApiMvc.service;

import com.eCommerce.couponApiMvc.dto.CouponIssueResultDto;
import com.eCommerce.couponApiMvc.repository.RedisRepository;
import com.eCommerce.couponApiMvc.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponApiMvc.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CouponRedisService {

    private final CouponCacheService couponCacheService;
    private final RedisRepository redisRepository;
    private final CouponIssueService couponIssueService;

    public CouponIssueResultDto issue(long couponId, String userId) {
        String FIRST_COME_TOPIC = "coupon-issue-requested";

        // ① 캐시 조회 (L1 Caffeine → L2 Redis → DB)
        CouponRedisEntity couponCache = couponCacheService.getCouponCache(couponId);

        // ② 발급 가능 여부 검증 (날짜·상태)
        couponCache.availableIssueableCoupon();

        // ③ Redis 선착순 발급 처리 (Lua 스크립트 — 원자적 중복·수량 체크)
        CouponIssueRequestCode code = redisRepository.issueRequest(couponId, userId, couponCache.totalQuantity());
        CouponIssueRequestCode.checkRequestResult(code);

        // ④ SUCCESS인 경우 DB에 발급 요청 저장
        if (code == CouponIssueRequestCode.SUCCESS) {
            Long requestId = couponIssueService.saveIssueRequestAndEventLog(couponCache.id(), userId, FIRST_COME_TOPIC);
            return new CouponIssueResultDto(code, requestId);
        }

        return new CouponIssueResultDto(code, null);
    }
}
