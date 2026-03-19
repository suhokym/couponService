package com.eCommerce.couponApi.dto;

import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;

public record CouponIssueResultDto(CouponIssueReqeustCode code, Long requestId) {
}
