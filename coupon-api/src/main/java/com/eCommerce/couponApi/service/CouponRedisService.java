package com.eCommerce.couponApi.service;


import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponApi.util.CouponRedisUtil;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static com.eCommerce.couponApi.util.CouponRedisUtil.getIssuedCouponUsers;

@RequiredArgsConstructor
@Service
public class CouponRedisService {

    private final CouponCacheService couponCacheService;
    private final RedisRepository redisRepository;
    private final CouponIssueService couponIssueService;


    public Mono<CouponIssueReqeustCode> issue(long couponId, String userId) {
        return couponCacheService.getCouponCache(couponId).flatMap(couponCache -> {
            couponCache.availableIssueableCoupon();// 쿠폰 기한 체크 쿠폰 상태 체크
                //선착순 쿠폰 체크 로직 구현


                return redisRepository.issueRequest(couponId, userId, couponCache.totalQuantity())
                        .doOnNext(CouponIssueReqeustCode::checkRequestResult)
                        .flatMap(code -> {
                            if (code == CouponIssueReqeustCode.SUCCESS) {
                                return Mono.fromCallable(() ->{
                                    couponIssueService.saveIssueRequestAndEventLog(couponCache.id(), userId);
                                    return code;
                                }).subscribeOn(Schedulers.boundedElastic());
                            }
                            return Mono.just(code);
                        });


                //기본적인 쿠폰일때 MVC 기반으로 쿠폰 발급 정합성 느리기
//                return redisRepository.sIsMember(getIssuedCouponUsers(couponId), userId).flatMap(isDuplication -> {
//                    if (isDuplication) {
//                        return Mono.error(new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급된 쿠폰입니다"));
//                    }
//                    return redisRepository.sAdd(getIssuedCouponUsers(couponId), userId)
//                            .map(i -> String.valueOf(i))
//                            .map(CouponIssueReqeustCode::findCode)
//                            .doOnNext(CouponIssueReqeustCode::checkRequestResult);
//                });
//            }

            });

            //이때 내가 해야할일 issuerequest와 couponeventlog에 저장
        }







}
