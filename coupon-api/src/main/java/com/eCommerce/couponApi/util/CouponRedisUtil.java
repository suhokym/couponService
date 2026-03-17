package com.eCommerce.couponApi.util;

public class CouponRedisUtil {


    public static String getIssuedCouponUsers(Long couponId) {
        return "coupon:campaign:%d:users".formatted(couponId);
    }
    public static String getCouponCacheKey(long couponId) {
        return "coupon:cache:%d".formatted(couponId);
    }

}
