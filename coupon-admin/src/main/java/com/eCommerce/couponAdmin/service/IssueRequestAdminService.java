package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueRequestAdminService {

    private final CouponIssueRequestRepository issueRequestRepository;

    /**
     * 발급 요청 목록 조회
     * - status 파라미터가 있으면 해당 상태로 필터링, 없으면 전체 반환
     */
    @Transactional(readOnly = true)
    public List<CouponIssueRequestDto> findAll(IssueRequestStatus status) {
        if (status != null) {
            // 특정 상태의 발급 요청만 조회
            return issueRequestRepository.findByStatus(status)
                    .stream()
                    .map(CouponIssueRequestDto::from)
                    .toList();
        }
        // 전체 발급 요청 조회
        return issueRequestRepository.findAll()
                .stream()
                .map(CouponIssueRequestDto::from)
                .toList();
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
