package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.service.EventLogAdminService;
import com.eCommerce.couponDomain.dto.CouponEventLogDto;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 이벤트 로그 어드민 컨트롤러
 * - requestId / processingStatus 복합 필터 지원
 */
@Tag(name = "이벤트 로그 모니터링", description = "쿠폰 이벤트 처리 로그 조회 API")
@RestController
@RequestMapping("/admin/event-logs")
@RequiredArgsConstructor
public class EventLogAdminController {

    private final EventLogAdminService eventLogAdminService;

    /**
     * 이벤트 로그 목록 조회
     * GET /admin/event-logs?requestId=&processingStatus=&page=0&size=20
     */
    @Operation(summary = "이벤트 로그 목록 조회",
            description = "requestId·processingStatus 복합 필터, createdAt DESC 정렬")
    @GetMapping
    public ResponseEntity<Page<CouponEventLogDto>> findAll(
            @RequestParam(required = false) Long requestId,
            @RequestParam(required = false) EventProcessingStatus processingStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                eventLogAdminService.findAll(requestId, processingStatus, page, size));
    }

    /**
     * 이벤트 로그 단건 조회
     * GET /admin/event-logs/{eventId}
     */
    @Operation(summary = "이벤트 로그 상세 조회")
    @GetMapping("/{eventId}")
    public ResponseEntity<CouponEventLogDto> findById(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventLogAdminService.findById(eventId));
    }
}
