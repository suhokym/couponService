package com.eCommerce.couponApi.util;

public class CouponRedisUtil {

    public static String getCouponIssueKey(String couponId) {
        return "coupon:issue:%s:issued-users".formatted(couponId);}

    public static String getCouponIssueKeys(String couponId) {
        return "coupon:issue:%s:issued-users".formatted(couponId);}


}
