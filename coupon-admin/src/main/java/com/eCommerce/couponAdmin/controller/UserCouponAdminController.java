package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.service.UserCouponAdminService;
import com.eCommerce.couponDomain.dto.UserCouponDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "유저 쿠폰 모니터링", description = "발급된 유저 쿠폰 현황 조회 API")
@RestController
@RequestMapping("/admin/user-coupons")
@RequiredArgsConstructor
public class UserCouponAdminController {

    private final UserCouponAdminService userCouponAdminService;

    /**
     * 유저 쿠폰 목록 조회 (서버사이드 페이지네이션)
     * GET /admin/user-coupons?userId=user1&page=0&size=20
     * - userId 쿼리 파라미터로 특정 유저의 쿠폰만 조회 가능 (없으면 전체 조회)
     */
    @Operation(summary = "유저 쿠폰 목록 조회", description = "userId 파라미터로 특정 유저 필터링 가능. page(0-based), size(기본 20)로 페이지네이션 지원")
    @GetMapping
    public ResponseEntity<Page<UserCouponDto>> findAll(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userCouponAdminService.findAll(userId, page, size));
    }
}
