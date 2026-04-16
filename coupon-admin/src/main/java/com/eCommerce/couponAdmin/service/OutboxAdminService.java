package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.OutboxEventDto;
import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import com.eCommerce.couponDomain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Outbox 어드민 모니터링 서비스
 * - publishStatus 탭 필터 + updatedAt DESC 정렬
 * ⚠️ NOTE: domain Repository에 Pageable 오버로드를 추가하지 않고,
 *           기존 findByPublishStatus(List 반환) + 메모리 페이지네이션으로 구현
 *           (어드민 전용, 데이터량 적음)
 */
@Service
@RequiredArgsConstructor
public class OutboxAdminService {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Outbox 이벤트 목록 조회
     * - status 있으면 해당 상태만 필터, 없으면 전체
     * - updatedAt DESC 정렬 (최근 활동 기준)
     */
    @Transactional(readOnly = true)
    public Page<OutboxEventDto> findAll(OutboxPublishStatus status, int page, int size) {
        List<OutboxEvent> events;

        if (status != null) {
            // 특정 publishStatus 필터 — 기존 메서드 활용
            events = outboxEventRepository.findByPublishStatus(status);
        } else {
            // 전체 조회
            events = outboxEventRepository.findAll();
        }

        // updatedAt DESC 정렬 (BaseTimeEntity 필드 — markPublished/markFailed 시 갱신됨)
        List<OutboxEvent> sorted = events.stream()
                .sorted(Comparator.comparing(OutboxEvent::getUpdatedAt).reversed())
                .collect(Collectors.toList());

        return toPage(sorted.stream().map(OutboxEventDto::from).collect(Collectors.toList()),
                page, size);
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
