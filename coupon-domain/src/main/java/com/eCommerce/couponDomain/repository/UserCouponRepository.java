package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByCampaign_CouponIdAndUserId(Long campaignId, String userId);

    List<UserCoupon> findByUserId(String userId);
}
