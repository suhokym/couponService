package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.UserCouponDto;
import com.eCommerce.couponDomain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCouponAdminService {

    private final UserCouponRepository userCouponRepository;

    /**
     * 유저 쿠폰 목록 조회 (서버사이드 페이지네이션)
     * - userId 파라미터가 있으면 해당 유저의 쿠폰만 반환, 없으면 전체 반환
     * - page: 0-based 페이지 번호, size: 페이지당 건수 (기본 20)
     */
    @Transactional(readOnly = true)
    public Page<UserCouponDto> findAll(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (userId != null && !userId.isBlank()) {
            // 특정 유저의 쿠폰만 페이징 조회
            return userCouponRepository.findByUserId(userId, pageable)
                    .map(UserCouponDto::from);
        }
        // 전체 유저 쿠폰 페이징 조회
        return userCouponRepository.findAll(pageable)
                .map(UserCouponDto::from);
    }
}
