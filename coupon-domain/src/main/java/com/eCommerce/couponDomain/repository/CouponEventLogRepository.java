package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponEventLogRepository extends JpaRepository<CouponEventLog, Long> {


    Optional<CouponEventLog> findByRequest_RequestId(Long requestId);


}
