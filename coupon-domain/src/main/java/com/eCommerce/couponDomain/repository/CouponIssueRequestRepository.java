package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Integer> {

    @Query("select ci from CouponIssueRequest ci where ci.campaign = :campaignId and ci.userId = :userId")
    CouponIssueRequest findByRequestIdAndUserId(long campaignId, String userId);
}
