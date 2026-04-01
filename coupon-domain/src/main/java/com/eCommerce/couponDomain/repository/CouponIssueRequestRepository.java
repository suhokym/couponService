package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

    CouponIssueRequest findByCampaign_CouponIdAndUserId(Long couponId, String userId);

    Optional<CouponIssueRequest> findByRequestId(Long requestId);

    List<CouponIssueRequest> findByStatus(IssueRequestStatus status);

    // 서버사이드 페이지네이션용 오버로드 (기존 List 반환 메서드 유지)
    Page<CouponIssueRequest> findByStatus(IssueRequestStatus status, Pageable pageable);

    // ⚠️ NOTE: PROCESSING 상태에서 updatedAt이 threshold 이전인 레코드 조회
    //          컨슈머 크래시로 처리가 중단된 stuck 레코드 복구용
    List<CouponIssueRequest> findByStatusAndUpdatedAtBefore(IssueRequestStatus status, LocalDateTime threshold);

    // ⚠️ NOTE: JOIN FETCH로 campaign을 즉시 로딩 — 트랜잭션 종료 후 스케줄러에서
    //          campaign 프록시 접근 시 LazyInitializationException 방지용
    @Query("SELECT r FROM CouponIssueRequest r JOIN FETCH r.campaign WHERE r.status = :status AND r.updatedAt < :threshold")
    List<CouponIssueRequest> findStuckWithCampaign(@Param("status") IssueRequestStatus status,
                                                   @Param("threshold") LocalDateTime threshold);
}
