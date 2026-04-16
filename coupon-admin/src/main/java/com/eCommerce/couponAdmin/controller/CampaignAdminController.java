package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.dto.CampaignCreateRequest;
import com.eCommerce.couponAdmin.dto.CampaignStatusUpdateRequest;
import com.eCommerce.couponAdmin.service.CampaignAdminService;
import com.eCommerce.couponDomain.dto.CouponCampaignDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpStatus;

@Tag(name = "캠페인 관리", description = "쿠폰 캠페인 생성 및 상태 관리 API")
@RestController
@RequestMapping("/admin/campaigns")
@RequiredArgsConstructor
public class CampaignAdminController {

    private final CampaignAdminService campaignAdminService;

    /**
     * 전체 캠페인 목록 조회 (서버사이드 페이지네이션)
     * GET /admin/campaigns?page=0&size=20
     */
    @Operation(summary = "캠페인 목록 조회", description = "page(0-based), size(기본 20) 파라미터로 페이지네이션 지원")
    @GetMapping
    public ResponseEntity<Page<CouponCampaignDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(campaignAdminService.findAll(page, size));
    }

    /**
     * 캠페인 단건 조회
     * GET /admin/campaigns/{couponId}
     */
    @Operation(summary = "캠페인 상세 조회")
    @GetMapping("/{couponId}")
    public ResponseEntity<CouponCampaignDto> findById(@PathVariable Long couponId) {
        return ResponseEntity.ok(campaignAdminService.findById(couponId));
    }

    /**
     * 캠페인 생성
     * POST /admin/campaigns
     */
    @Operation(summary = "캠페인 생성", description = "생성 직후 상태는 INACTIVE. 준비 완료 후 ACTIVE로 변경하세요.")
    @PostMapping
    public ResponseEntity<CouponCampaignDto> create(@RequestBody CampaignCreateRequest request) {
        return ResponseEntity.ok(campaignAdminService.create(request));
    }

    /**
     * 캠페인 상태 변경 (ACTIVE / INACTIVE / ENDED)
     * PATCH /admin/campaigns/{couponId}/status
     */
    @Operation(summary = "캠페인 상태 변경")
    @PatchMapping("/{couponId}/status")
    public ResponseEntity<CouponCampaignDto> updateStatus(
            @PathVariable Long couponId,
            @RequestBody CampaignStatusUpdateRequest request) {
        return ResponseEntity.ok(campaignAdminService.updateStatus(couponId, request));
    }

    /**
     * 캠페인 삭제 (INACTIVE / ENDED 상태만 허용)
     * DELETE /admin/campaigns/{couponId}
     */
    @Operation(summary = "캠페인 삭제", description = "ACTIVE 상태 캠페인은 삭제 불가. INACTIVE 또는 ENDED 상태만 삭제 가능.")
    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable Long couponId) {
        campaignAdminService.delete(couponId);
        return ResponseEntity.noContent().build();
    }
}
