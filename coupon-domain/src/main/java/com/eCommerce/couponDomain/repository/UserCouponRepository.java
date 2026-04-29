package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.UserCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, String userId);

    List<UserCoupon> findByUserId(String userId);

    // 서버사이드 페이지네이션용 오버로드 (기존 List 반환 메서드 유지)
    Page<UserCoupon> findByUserId(String userId, Pageable pageable);

    // ⚠️ NOTE: 벌크 처리 후 캠페인 IssuedQuantity 동기화용 — INCREMENT 방식 대신
    //          실제 발급 수를 COUNT하여 정확한 값으로 갱신
    long countByCouponId(Long couponId);

    // ⚠️ NOTE: Step1 Pre-fetch용 — 배치 전체의 기발급 (couponId, userId) 쌍을 1쿼리로 조회
    //          건별 existsByCouponIdAndUserId 대신 IN 절 벌크 조회로 N×1 → 1 쿼리 절감
    List<UserCoupon> findByCouponIdInAndUserIdIn(List<Long> couponIds, List<String> userIds);

}
