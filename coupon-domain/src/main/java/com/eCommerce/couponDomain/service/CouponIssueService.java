package com.eCommerce.couponDomain.service;

import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.UserCoupon;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponCampaignJpaRepository;
import com.eCommerce.couponDomain.repository.CouponEventLogRepository;
import com.eCommerce.couponDomain.repository.CouponIssueRequestRepository;
import com.eCommerce.couponDomain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CouponIssueService {

    private final CouponCampaignJpaRepository couponCampaignJpaRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponEventLogRepository couponEventLogRepository;
    private final UserCouponRepository userCouponRepository;


    //쿠폰 발급


    //쿠폰 정보 조회
    @Transactional(readOnly = true)
    public CouponCampaign findCoupon(long couponId) {
        return couponCampaignJpaRepository.findById(couponId).orElseThrow(() ->{
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST,"존재하지 않는 쿠폰입니다 couponId=%d".formatted(couponId));
        });
    }

    @Transactional
    public void saveCouponIssueRequest(CouponIssueRequest event) {
        try {
            couponIssueRequestRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 대기열 발급에 실패했습니다");
        }

    }

    @Transactional
    public void saveUserCoupon(UserCoupon event) {
        try {
            userCouponRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 발급에 실패했습니다");
        }

    }

    @Transactional
    public void saveCouponEventLong(CouponEventLog event) {
        try {
            couponEventLogRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 발급에 실패했습니다");
        }

    }






}
