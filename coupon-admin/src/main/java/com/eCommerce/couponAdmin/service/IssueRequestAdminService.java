package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueRequestAdminService {

    private final CouponIssueRequestRepository issueRequestRepository;

    /**
     * 발급 요청 목록 조회 (서버사이드 페이지네이션)
     * - status 파라미터가 있으면 해당 상태로 필터링, 없으면 전체 반환
     * - page: 0-based 페이지 번호, size: 페이지당 건수 (기본 20)
     */
    @Transactional(readOnly = true)
    public Page<CouponIssueRequestDto> findAll(IssueRequestStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (status != null) {
            // 특정 상태의 발급 요청만 페이징 조회
            return issueRequestRepository.findByStatus(status, pageable)
                    .map(CouponIssueRequestDto::from);
        }
        // 전체 발급 요청 페이징 조회
        return issueRequestRepository.findAll(pageable)
                .map(CouponIssueRequestDto::from);
    }

    /**
     * 발급 요청 단건 조회
     */
    @Transactional(readOnly = true)
    public CouponIssueRequestDto findById(Long requestId) {
        return issueRequestRepository.findById(requestId)
                .map(CouponIssueRequestDto::from)
                .orElseThrow(() -> new CouponIssueException(
                        ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND,
                        "존재하지 않는 발급 요청입니다. requestId=%d".formatted(requestId)));
    }
}
