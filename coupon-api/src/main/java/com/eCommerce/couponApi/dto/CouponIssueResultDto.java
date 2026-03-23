package com.eCommerce.couponApi.dto;

import com.eCommerce.couponApi.repository.redisDto.CouponIssueRequestCode;

public record CouponIssueResultDto(CouponIssueRequestCode code, Long requestId) {
}
