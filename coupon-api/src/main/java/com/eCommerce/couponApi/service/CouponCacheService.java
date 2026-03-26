package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.eCommerce.couponApi.util.CouponRedisUtil.getCouponCacheKey;

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
    public Mono<CouponRedisEntity> getCouponCache(long couponId) {
        // 1. L1 로컬 캐시 조회 (가장 빠름, 네트워크 없음)
        CouponRedisEntity local = localCache.getIfPresent(couponId);
        if (local != null) {
            return Mono.just(local);
        }

        // 2. L2 Redis 캐시 조회 → 히트 시 L1에도 올려두기
        //    미스 시 → DB 조회(락 적용) → 결과를 L1에도 올려두기
        String cacheKey = getCouponCacheKey(couponId);
        String lockKey = cacheKey + ":lock";

        return getFromRedis(cacheKey)
                .doOnNext(entity -> localCache.put(couponId, entity))         // L2 히트 → L1 승격
                .switchIfEmpty(Mono.defer(() -> loadFromDbWithLock(couponId, cacheKey, lockKey)
                        .doOnNext(entity -> localCache.put(couponId, entity)))); // DB 히트 → L1 승격
    }

    /**
     * Redis에서 JSON 문자열을 읽어 CouponRedisEntity로 역직렬화
     * Redis에 키가 없으면 empty Mono 반환 (switchIfEmpty 로 이어짐)
     */
    private Mono<CouponRedisEntity> getFromRedis(String cacheKey) {
        return redisRepository.get(cacheKey).flatMap(json -> {
            try {
                return Mono.just(objectMapper.readValue(json, CouponRedisEntity.class));
            } catch (JsonProcessingException e) {
                return Mono.error(new RuntimeException("캐시 역직렬화 실패 : %s".formatted(e.getMessage())));
            }
        });
    }

    /**
     * 분산 락을 사용한 DB 조회 + Redis 캐싱
     *
     * [락 획득 성공 경로]
     *   1. Redis double-check: 락 대기 중 다른 스레드가 이미 캐싱했을 수 있음
     *   2. 여전히 없으면 DB 조회 후 Redis에 저장
     *   3. 성공/실패 모두 unlock 후 결과 반환
     *
     *   ⚠️ NOTE: unlock을 doFinally().subscribe()가 아닌 flatMap/onErrorResume 체인에 포함한 이유
     *      - doFinally().subscribe()는 fire-and-forget → unlock이 완료되기 전에 subscriber에게
     *        terminal signal이 전달되는 race condition 발생 가능
     *      - flatMap/onErrorResume 방식은 unlock이 반드시 완료된 후 signal을 전달함을 보장
     *
     * [락 획득 실패 경로]
     *   1. 200ms 대기 (다른 스레드의 DB→Redis 캐싱 완료 기대)
     *   2. Redis 재조회 → 여전히 없으면 재귀 재시도
     */
    private Mono<CouponRedisEntity> loadFromDbWithLock(long couponId, String cacheKey, String lockKey) {
        return redisRepository.tryLock(lockKey, Duration.ofSeconds(5))
                .flatMap(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        // [락 획득 성공]
                        // 1. double-check: 대기 중 다른 스레드가 캐싱했을 수 있으니 Redis 재확인
                        // 2. 없으면 DB 조회 후 Redis 저장
                        Mono<CouponRedisEntity> result = getFromRedis(cacheKey)
                                .switchIfEmpty(Mono.defer(() -> loadFromDbAndCache(couponId, cacheKey)));

                        // 3. 성공 시: unlock 완료 후 entity 반환
                        // 4. 실패 시: unlock 완료 후 에러 재전파
                        return result
                                .flatMap(entity -> redisRepository.unlock(lockKey).thenReturn(entity))
                                .onErrorResume(e -> redisRepository.unlock(lockKey).then(Mono.error(e)));
                    } else {
                        // [락 획득 실패] 다른 스레드가 DB 조회 중 → 잠시 대기 후 Redis 재조회
                        return Mono.delay(Duration.ofMillis(200))
                                .flatMap(ignored -> getFromRedis(cacheKey))
                                // 여전히 없으면 재시도 (재귀)
                                .switchIfEmpty(Mono.defer(() -> loadFromDbWithLock(couponId, cacheKey, lockKey)));
                    }
                });
    }

    /**
     * DB에서 쿠폰을 조회하고 Redis에 캐싱
     *
     * ⚠️ subscribeOn(boundedElastic()): couponIssueService.findCoupon()은 블로킹 JPA 호출이므로
     *    Reactor의 논블로킹 스레드(netty I/O)를 점유하지 않도록 boundedElastic 스레드풀에서 실행
     *
     * 흐름: DB 조회 → CouponRedisEntity 변환 → JSON 직렬화 → Redis 저장 → entity 반환
     */
    private Mono<CouponRedisEntity> loadFromDbAndCache(long couponId, String cacheKey) {
        return Mono.fromCallable(() -> couponIssueService.findCoupon(couponId)) // 블로킹 DB 호출
                .subscribeOn(Schedulers.boundedElastic())                        // 블로킹 전용 스레드풀
                .map(CouponRedisEntity::new)                                     // 도메인 → Redis 엔티티 변환
                .flatMap(entity -> {
                    try {
                        String json = objectMapper.writeValueAsString(entity);   // JSON 직렬화
                        // Redis에 저장 후 entity 반환 (TTL 1시간)
                        return redisRepository.set(cacheKey, json, Duration.ofHours(1)).thenReturn(entity);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("캐시 직렬화 실패 : %s".formatted(e.getMessage())));
                    }
                });
    }
}
