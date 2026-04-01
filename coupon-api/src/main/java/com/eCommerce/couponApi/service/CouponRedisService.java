package com.eCommerce.couponApi.service;


import com.eCommerce.couponApi.dto.CouponIssueResponseDto;
import com.eCommerce.couponApi.dto.CouponIssueResultDto;
import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponApi.util.CouponRedisUtil;
import com.eCommerce.couponDomain.entity.CouponCampaign;
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
import static com.eCommerce.couponDomain.exception.ErrorCode.FAIL_COUPON_ISSUE_REQUEST;

@RequiredArgsConstructor
@Service
public class CouponRedisService {

    private final CouponCacheService couponCacheService;
    private final RedisRepository redisRepository;
    private final CouponIssueService couponIssueService;


    public Mono<CouponIssueResultDto> issueCoupon(long couponId, String userId) {
       return couponCacheService.getCouponCache(couponId)
               .checkpoint("쿠폰 캐시 조회 완료 coupon=" + couponId)
               .flatMap(couponCache -> Mono.fromRunnable(couponCache::availableIssueableCoupon)
                       .thenReturn(couponCache)
               ).checkpoint("쿠폰 발급 가능 여부 검증 완료")
               .flatMap(couponCache -> switch (couponCache.campaignType()){
                   case FIRST_COME -> issueFirstCome(couponCache, userId);
                   case OPEN ->  issueOpenCoupon(couponId, userId);
                   case MANUAL -> Mono.error(new CouponIssueException(FAIL_COUPON_ISSUE_REQUEST, "관리자 발급은 admin API를 이용하세요"));
                   default -> Mono.error(new CouponIssueException(FAIL_COUPON_ISSUE_REQUEST, "지원하지 않는 쿠폰 타입입니다 :%s".formatted(couponCache.campaignType())));
               });
    }

    //지금 중복체크가 되자않고있음
    public Mono<CouponIssueResultDto> issueOpenCoupon(long couponId, String userId){
        String OPEN_COUPON_TOPIC = "open-coupon-issue-requested";

        return redisRepository.issueRequest(couponId, userId, null)
                .doOnNext(CouponIssueRequestCode::checkRequestResult)
                .checkpoint("Redis 중복 발급 처리 완료")
                .flatMap(code -> {
                    if (code == CouponIssueRequestCode.SUCCESS_OPEN){
                        return Mono.fromCallable(() -> {
                                    Long requestId = couponIssueService.saveIssueRequestAndEventLog(couponId, userId, OPEN_COUPON_TOPIC);
                                    return new CouponIssueResultDto(CouponIssueRequestCode.SUCCESS_OPEN, requestId);
                                }).subscribeOn(Schedulers.boundedElastic())
                                .checkpoint("OPEN 쿠폰 발급  요청 저장 완료");
                    }
                    return Mono.just(new CouponIssueResultDto(code, null));
                });

    }





    public Mono<CouponIssueResultDto> issueFirstCome(CouponRedisEntity coupon, String userId) {
        String FIRST_COME_TOPIC = "first-coupon-issue-requested";
        // ⚠️ NOTE: availableIssueableCoupon()을 Mono.fromRunnable()로 래핑 — flatMap 내부 동기 예외를
        //          명시적으로 에러 signal로 변환해 리액티브 관례를 준수하고 의도를 명확히 함
        //          void 반환 메서드이므로 fromRunnable 사용 후 thenReturn으로 couponCache 복원
                    return redisRepository.issueRequest(coupon.id(), userId, coupon.totalQuantity())
                            .doOnNext(CouponIssueRequestCode::checkRequestResult)
                            // ③ Redis Lua 스크립트 단계 — 중복 발급·수량 초과·스크립트 오류 식별
                            .checkpoint("Redis 선착순 발급 처리 완료")
                            .flatMap(code -> {
                                if (code == CouponIssueRequestCode.SUCCESS_FIRST_COME) {
                                    return Mono.fromCallable(() -> {
                                                Long requestId = couponIssueService.saveIssueRequestAndEventLog(coupon.id(), userId, FIRST_COME_TOPIC);
                                                return new CouponIssueResultDto(code, requestId);
                                            }).subscribeOn(Schedulers.boundedElastic())
                                            // ④ DB 저장 단계 — JPA save 실패·CouponIssueException 식별
                                            .checkpoint("DB 발급 요청 저장 완료");
                                }
                                return Mono.just(new CouponIssueResultDto(code, null));
                            });

    }







}
