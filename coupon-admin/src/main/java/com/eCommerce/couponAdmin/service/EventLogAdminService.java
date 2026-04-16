package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.CouponEventLogDto;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이벤트 로그 어드민 조회 서비스
 * - requestId / processingStatus 복합 필터 지원
 * - domain Repository 메서드 추가 없이 기존 메서드 + 메모리 필터로 구현
 * ⚠️ NOTE: 어드민 전용 — 트래픽이 낮으므로 전체 조회 후 메모리 페이지네이션으로 단순화
 */
@Service
@RequiredArgsConstructor
public class EventLogAdminService {

    private final CouponEventLogRepository eventLogRepository;

    /**
     * 이벤트 로그 목록 조회
     * - requestId + status 둘 다 있으면 복합 필터
     * - 하나만 있으면 단일 필터
     * - 둘 다 없으면 전체 (createdAt DESC, JPA 페이징)
     */
    @Transactional(readOnly = true)
    public Page<CouponEventLogDto> findAll(Long requestId, EventProcessingStatus status, int page, int size) {
        // requestId 없이 전체 조회 — JPA Pageable로 DB 레벨 페이징
        if (requestId == null && status == null) {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            return eventLogRepository.findAll(pageable).map(CouponEventLogDto::from);
        }

        // requestId 있음 — 해당 요청의 전체 로그를 조회한 뒤 메모리 필터/페이징
        if (requestId != null) {
            List<CouponEventLog> logs = eventLogRepository
                    .findByRequest_RequestIdOrderByCreatedAtAsc(requestId);

            // createdAt DESC로 역정렬 후 status 추가 필터 적용
            List<CouponEventLogDto> result = logs.stream()
                    .sorted(Comparator.comparing(CouponEventLog::getCreatedAt).reversed())
                    .filter(l -> status == null || l.getProcessingStatus() == status)
                    .map(CouponEventLogDto::from)
                    .collect(Collectors.toList());

            return toPage(result, page, size);
        }

        // status만 있는 경우 — 전체 조회 후 메모리 필터
        List<CouponEventLog> all = eventLogRepository.findAll(Sort.by("createdAt").descending());
        List<CouponEventLogDto> result = all.stream()
                .filter(l -> l.getProcessingStatus() == status)
                .map(CouponEventLogDto::from)
                .collect(Collectors.toList());
        return toPage(result, page, size);
    }

    /**
     * 이벤트 로그 단건 조회
     */
    @Transactional(readOnly = true)
    public CouponEventLogDto findById(Long eventId) {
        return eventLogRepository.findById(eventId)
                .map(CouponEventLogDto::from)
                .orElseThrow(() -> new CouponIssueException(
                        ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,
                        "존재하지 않는 이벤트 로그입니다. eventId=%d".formatted(eventId)));
    }

    /**
     * List → PageImpl 변환 헬퍼
     */
    private <T> Page<T> toPage(List<T> list, int page, int size) {
        int total = list.size();
        int start = page * size;
        if (start >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), total);
        }
        int end = Math.min(start + size, total);
        return new PageImpl<>(list.subList(start, end), PageRequest.of(page, size), total);
    }
}
