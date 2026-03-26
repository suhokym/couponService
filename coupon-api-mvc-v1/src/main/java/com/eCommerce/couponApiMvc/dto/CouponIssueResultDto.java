package com.eCommerce.couponApiMvc.dto;

import com.eCommerce.couponApiMvc.repository.redisDto.CouponIssueRequestCode;

public record CouponIssueResultDto(CouponIssueRequestCode code, Long requestId) {
}
