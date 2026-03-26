package com.eCommerce.couponApiMvc.service;

import com.eCommerce.couponApiMvc.repository.RedisRepository;
import com.eCommerce.couponApiMvc.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.eCommerce.couponApiMvc.util.CouponRedisUtil.getCouponCacheKey;

/**
 * 쿠폰 캐시 서비스 (2-Tier 캐시 구조)
 *
 * 조회 우선순위:
 *   L1. 로컬 캐시 (Caffeine) - 메모리, TTL 5분, 최대 1000개
 *   L2. Redis 캐시            - 분산 캐시, TTL 1시간
 *   DB. DB 직접 조회          - 위 두 캐시 모두 미스일 때만 실행 (분산 락 사용)
 */
@Service
public class CouponCacheService {

    private final CouponIssueService couponIssueService;
    private final RedisRepository redisRepository;
    private final ObjectMapper objectMapper;

    // L1 로컬 캐시: 5분 TTL, 최대 1000개
    private final Cache<Long, CouponRedisEntity> localCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    public CouponCacheService(CouponIssueService couponIssueService,
                               RedisRepository redisRepository,
                               ObjectMapper objectMapper) {
        this.couponIssueService = couponIssueService;
        this.redisRepository = redisRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 쿠폰 캐시 조회 (L1 → L2 → DB 순서)
     */
    public CouponRedisEntity getCouponCache(long couponId) {
        // 1. L1 로컬 캐시 조회
        CouponRedisEntity local = localCache.getIfPresent(couponId);
        if (local != null) {
            return local;
        }

        // 2. L2 Redis 캐시 조회
        String cacheKey = getCouponCacheKey(couponId);
        CouponRedisEntity fromRedis = getFromRedis(cacheKey);
        if (fromRedis != null) {
            localCache.put(couponId, fromRedis); // L2 히트 → L1 승격
            return fromRedis;
        }

        // 3. DB 조회 (분산 락 사용)
        String lockKey = cacheKey + ":lock";
        CouponRedisEntity fromDb = loadFromDbWithLock(couponId, cacheKey, lockKey);
        localCache.put(couponId, fromDb); // DB 히트 → L1 승격
        return fromDb;
    }

    /**
     * Redis에서 JSON 문자열을 읽어 CouponRedisEntity로 역직렬화
     */
    private CouponRedisEntity getFromRedis(String cacheKey) {
        String json = redisRepository.get(cacheKey);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, CouponRedisEntity.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 역직렬화 실패: %s".formatted(e.getMessage()));
        }
    }

    /**
     * 분산 락을 사용한 DB 조회 + Redis 캐싱
     *
     * ⚠️ NOTE: synchronized 대신 Redis 분산 락 사용 — 다중 인스턴스 환경에서도 단일 DB 조회 보장
     */
    private CouponRedisEntity loadFromDbWithLock(long couponId, String cacheKey, String lockKey) {
        Boolean acquired = redisRepository.tryLock(lockKey, Duration.ofSeconds(5));
        if (Boolean.TRUE.equals(acquired)) {
            try {
                // double-check: 락 대기 중 다른 스레드가 캐싱했을 수 있음
                CouponRedisEntity doubleCheck = getFromRedis(cacheKey);
                if (doubleCheck != null) return doubleCheck;
                return loadFromDbAndCache(couponId, cacheKey);
            } finally {
                redisRepository.unlock(lockKey);
            }
        } else {
            // 락 획득 실패 → 잠시 대기 후 Redis 재조회
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            CouponRedisEntity retryFromRedis = getFromRedis(cacheKey);
            if (retryFromRedis != null) return retryFromRedis;
            return loadFromDbWithLock(couponId, cacheKey, lockKey); // 재시도
        }
    }

    /**
     * DB에서 쿠폰을 조회하고 Redis에 캐싱
     */
    private CouponRedisEntity loadFromDbAndCache(long couponId, String cacheKey) {
        CouponRedisEntity entity = new CouponRedisEntity(couponIssueService.findCoupon(couponId));
        try {
            String json = objectMapper.writeValueAsString(entity);
            redisRepository.set(cacheKey, json, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 직렬화 실패: %s".formatted(e.getMessage()));
        }
        return entity;
    }
}
