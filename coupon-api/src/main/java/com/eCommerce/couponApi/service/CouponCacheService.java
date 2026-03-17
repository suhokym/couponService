package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.redis.CouponRedisEntity;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CouponCacheService {

    CouponIssueService couponIssueService;

    @Cacheable(cacheNames = "coupon")
    public CouponRedisEntity getCouponCache(long couponId){
        CouponCampaign coupon = couponIssueService.findCoupon(couponId);
        return new CouponRedisEntity(coupon);
    }


}
