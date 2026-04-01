package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.UserCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByCampaign_CouponIdAndUserId(Long campaignId, String userId);

    List<UserCoupon> findByUserId(String userId);

    // 서버사이드 페이지네이션용 오버로드 (기존 List 반환 메서드 유지)
    Page<UserCoupon> findByUserId(String userId, Pageable pageable);
}
