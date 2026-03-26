package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

    CouponIssueRequest findByCampaign_CouponIdAndUserId(Long couponId, String userId);

    Optional<CouponIssueRequest> findByRequestId(Long requestId);

    List<CouponIssueRequest> findByStatus(IssueRequestStatus status);
}
