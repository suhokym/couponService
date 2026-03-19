package com.eCommerce.couponApi.dto;

public record CouponIssueKafkaDto(Long couponId, String userId,  Long couponIssueRequestId) {
}
