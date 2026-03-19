package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

    @Query("select ci from CouponIssueRequest ci where ci.campaign.couponId = :campaignId and ci.userId = :userId")
    CouponIssueRequest findByRequestIdAndUserId(@Param("campaignId") long campaignId, @Param("userId") String userId);
}
