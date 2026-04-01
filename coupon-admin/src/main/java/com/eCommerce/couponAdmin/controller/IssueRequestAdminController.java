package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.service.IssueRequestAdminService;
import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "발급 요청 모니터링", description = "쿠폰 발급 요청 현황 조회 API")
@RestController
@RequestMapping("/admin/issue-requests")
@RequiredArgsConstructor
public class IssueRequestAdminController {

    private final IssueRequestAdminService issueRequestAdminService;

    /**
     * 발급 요청 목록 조회 (서버사이드 페이지네이션)
     * GET /admin/issue-requests?status=ISSUED&page=0&size=20
     * - status 쿼리 파라미터로 필터링 가능 (없으면 전체 조회)
     */
    @Operation(summary = "발급 요청 목록 조회", description = "status 파라미터로 필터링 가능. page(0-based), size(기본 20)로 페이지네이션 지원")
    @GetMapping
    public ResponseEntity<Page<CouponIssueRequestDto>> findAll(
            @RequestParam(required = false) IssueRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(issueRequestAdminService.findAll(status, page, size));
    }

    /**
     * 발급 요청 단건 조회
     * GET /admin/issue-requests/{requestId}
     */
    @Operation(summary = "발급 요청 상세 조회")
    @GetMapping("/{requestId}")
    public ResponseEntity<CouponIssueRequestDto> findById(@PathVariable Long requestId) {
        return ResponseEntity.ok(issueRequestAdminService.findById(requestId));
    }
}
