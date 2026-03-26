package com.eCommerce.couponDomain.dto;

// ⚠️ NOTE: retry 토픽 전용 DTO.
//          CouponIssueEventDto에 failReason만 추가한 형태로,
//          컨슈머가 수신 시 왜 재시도가 발생했는지 로그/처리에 활용할 수 있게 한다.
public record CouponIssueRetryEventDto(
        Long couponId,
        String userId,
        Long couponIssueRequestId,
        String failReason   // 실패 사유 (예: "STEP1_VALIDATION", "DB 연결 실패" 등)
) {}
