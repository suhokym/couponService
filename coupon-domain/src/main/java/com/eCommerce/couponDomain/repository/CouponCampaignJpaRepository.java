package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponCampaignJpaRepository extends JpaRepository<CouponCampaign, Long> {
}
