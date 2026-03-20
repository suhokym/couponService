package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

    CouponIssueRequest findByCampaign_CouponIdAndUserId(Long couponId, String userId);

    Optional<CouponIssueRequest> findByRequestId(Long requestId);
}
