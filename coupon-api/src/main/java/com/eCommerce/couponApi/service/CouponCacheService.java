package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponApi.util.CouponRedisUtil;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

import static com.eCommerce.couponApi.util.CouponRedisUtil.getCouponCacheKey;


@RequiredArgsConstructor
@Service
public class CouponCacheService {

    private final CouponIssueService couponIssueService;
    private final RedisRepository redisRepository;
    private final ObjectMapper objectMapper;

    public Mono<CouponRedisEntity> getCouponCache(long couponId){
        String cacheKey = getCouponCacheKey(couponId);

       return redisRepository.get(cacheKey).flatMap(json -> {
            try {
                return Mono.just(objectMapper.readValue(json, CouponRedisEntity.class));
            }catch (JsonProcessingException e){
                return Mono.error(new RuntimeException("캐시 역직렬화 실패 : %s".formatted(e.getMessage())));
            }
        }).switchIfEmpty(
                Mono.fromCallable(() -> couponIssueService.findCoupon(couponId))
               .subscribeOn(Schedulers.boundedElastic()).map(CouponRedisEntity::new)
               .flatMap(entity -> {
                   try {
                       String json = objectMapper.writeValueAsString(entity);
                       return redisRepository.set(cacheKey,json, Duration.ofHours(1)).thenReturn(entity);
                   }catch (JsonProcessingException e){
                       return Mono.error(new RuntimeException("캐시 역직렬화 실패 : %s".formatted(e.getMessage())));
                   }
               }));
    }
    // 로컬 캐시 만들어야함.



}
