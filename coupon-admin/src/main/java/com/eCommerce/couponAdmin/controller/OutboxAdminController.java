package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.service.OutboxAdminService;
import com.eCommerce.couponDomain.dto.OutboxEventDto;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Outbox 어드민 모니터링 컨트롤러
 * - PENDING / PUBLISHED / FAILED 탭 필터 지원
 */
@Tag(name = "Outbox 모니터링", description = "Transactional Outbox 이벤트 발행 현황 조회 API")
@RestController
@RequestMapping("/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    /**
     * Outbox 이벤트 목록 조회
     * GET /admin/outbox?publishStatus=PENDING&page=0&size=20
     */
    @Operation(summary = "Outbox 이벤트 목록 조회",
            description = "publishStatus(PENDING/PUBLISHED/FAILED) 필터, updatedAt DESC 정렬")
    @GetMapping
    public ResponseEntity<Page<OutboxEventDto>> findAll(
            @RequestParam(required = false) OutboxPublishStatus publishStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(outboxAdminService.findAll(publishStatus, page, size));
    }
}
