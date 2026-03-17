package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Integer> {
}
