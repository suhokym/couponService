package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponEventLogRepository extends JpaRepository<CouponEventLog, Long> {
}
