package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByCampaignIdAndUserId(Long campaignId, String userId);


}
