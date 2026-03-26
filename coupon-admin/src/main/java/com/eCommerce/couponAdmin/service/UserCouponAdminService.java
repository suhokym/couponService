package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.UserCouponDto;
import com.eCommerce.couponDomain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCouponAdminService {

    private final UserCouponRepository userCouponRepository;

    /**
     * 유저 쿠폰 목록 조회
     * - userId 파라미터가 있으면 해당 유저의 쿠폰만 반환, 없으면 전체 반환
     */
    @Transactional(readOnly = true)
    public List<UserCouponDto> findAll(String userId) {
        if (userId != null && !userId.isBlank()) {
            // 특정 유저의 쿠폰만 조회
            return userCouponRepository.findByUserId(userId)
                    .stream()
                    .map(UserCouponDto::from)
                    .toList();
        }
        // 전체 유저 쿠폰 조회
        return userCouponRepository.findAll()
                .stream()
                .map(UserCouponDto::from)
                .toList();
    }
}
