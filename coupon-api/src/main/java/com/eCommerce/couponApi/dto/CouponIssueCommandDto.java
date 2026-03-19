package com.eCommerce.couponApi.dto;

public record CouponIssueCommandDto(Long couponId, String userId,  Long couponIssueRequestId) {
}
