package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponCacheServiceTest {

    @Mock
    private CouponIssueService couponIssueService;

    @Mock
    private RedisRepository redisRepository;

    private CouponCacheService couponCacheService;
    private ObjectMapper objectMapper;

    private static final long COUPON_ID = 1L;
    private static final String CACHE_KEY = "coupon:cache:1";
    private static final String LOCK_KEY = "coupon:cache:1:lock";

    private CouponRedisEntity sampleEntity;
    private CouponCampaign sampleCampaign;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        couponCacheService = new CouponCacheService(couponIssueService, redisRepository, objectMapper);

        sampleCampaign = CouponCampaign.builder()
                .couponId(COUPON_ID)
                .name("테스트 쿠폰")
                .type(CampaignType.FIRST_COME)
                .totalQuantity(100)
                .status(CampaignStatus.ACTIVE)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        sampleEntity = new CouponRedisEntity(sampleCampaign);
    }

    // ──────────────────────────────────────────────
    // L1: 로컬 캐시
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("L1 로컬 캐시 히트 - Redis/DB 조회 없음")
    void localCacheHit_skipsRedisAndDb() throws Exception {
        // 첫 번째 요청으로 로컬 캐시 워밍업 (Redis 히트)
        String json = objectMapper.writeValueAsString(sampleEntity);
        given(redisRepository.get(CACHE_KEY)).willReturn(Mono.just(json));

        couponCacheService.getCouponCache(COUPON_ID).block();

        // 두 번째 요청: 로컬 캐시에서 즉시 반환 → Redis 추가 호출 없음
        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectNextMatches(entity -> entity.id().equals(COUPON_ID))
                .verifyComplete();

        verify(redisRepository, times(1)).get(any()); // 첫 번째 요청에서만 호출
        verify(couponIssueService, never()).findCoupon(anyLong());
    }

    @Test
    @DisplayName("L1 로컬 캐시 히트 - DB 조회 경로도 로컬 캐시에 저장됨")
    void dbLoadResult_savedToLocalCache() throws Exception {
        String json = objectMapper.writeValueAsString(sampleEntity);

        // 첫 번째 요청: 캐시 미스 → DB 조회
        given(redisRepository.get(CACHE_KEY)).willReturn(Mono.empty());
        given(redisRepository.tryLock(eq(LOCK_KEY), any(Duration.class))).willReturn(Mono.just(true));
        given(couponIssueService.findCoupon(COUPON_ID)).willReturn(sampleCampaign);
        given(redisRepository.set(eq(CACHE_KEY), anyString(), any(Duration.class))).willReturn(Mono.just(true));
        given(redisRepository.unlock(LOCK_KEY)).willReturn(Mono.just(1L));

        couponCacheService.getCouponCache(COUPON_ID).block();

        // 두 번째 요청: 로컬 캐시 히트
        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectNextMatches(entity -> entity.id().equals(COUPON_ID))
                .verifyComplete();

        verify(couponIssueService, times(1)).findCoupon(COUPON_ID); // DB는 한 번만 조회
    }

    // ──────────────────────────────────────────────
    // L2: Redis 캐시
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("L2 Redis 캐시 히트 - DB 조회 없음, 결과 로컬 캐시에 저장")
    void redisCacheHit_noDbCall() throws Exception {
        String json = objectMapper.writeValueAsString(sampleEntity);
        given(redisRepository.get(CACHE_KEY)).willReturn(Mono.just(json));

        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectNextMatches(entity -> entity.id().equals(COUPON_ID)
                        && entity.campaignStatus() == CampaignStatus.ACTIVE)
                .verifyComplete();

        verify(couponIssueService, never()).findCoupon(anyLong());
        verify(redisRepository, never()).tryLock(any(), any());
    }

    @Test
    @DisplayName("Redis 역직렬화 실패 시 에러 전파")
    void redisCacheHit_deserializationFail_propagatesError() {
        given(redisRepository.get(CACHE_KEY)).willReturn(Mono.just("invalid-json"));

        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && e.getMessage().contains("캐시 역직렬화 실패"))
                .verify();
    }

    // ──────────────────────────────────────────────
    // DB 조회 + 분산 락
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("캐시 미스 - 락 획득 성공 후 DB 조회 및 Redis 저장")
    void cacheMiss_lockAcquired_loadsFromDbAndCaches() throws Exception {
        given(redisRepository.get(CACHE_KEY)).willReturn(Mono.empty());
        given(redisRepository.tryLock(eq(LOCK_KEY), any(Duration.class))).willReturn(Mono.just(true));
        given(couponIssueService.findCoupon(COUPON_ID)).willReturn(sampleCampaign);
        given(redisRepository.set(eq(CACHE_KEY), anyString(), eq(Duration.ofHours(1)))).willReturn(Mono.just(true));
        given(redisRepository.unlock(LOCK_KEY)).willReturn(Mono.just(1L));

        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectNextMatches(entity -> entity.id().equals(COUPON_ID))
                .verifyComplete();

        verify(couponIssueService).findCoupon(COUPON_ID);
        verify(redisRepository).set(eq(CACHE_KEY), anyString(), eq(Duration.ofHours(1)));
        verify(redisRepository).unlock(LOCK_KEY);
    }

    @Test
    @DisplayName("락 획득 성공 후 double-check 시 Redis 히트 - DB 조회 없음")
    void lockAcquired_doubleCheckHit_noDbCall() throws Exception {
        // 락 획득 전 get: 미스 → 락 획득 후 double-check get: 히트
        String json = objectMapper.writeValueAsString(sampleEntity);
        given(redisRepository.get(CACHE_KEY))
                .willReturn(Mono.empty())
                .willReturn(Mono.just(json));
        given(redisRepository.tryLock(eq(LOCK_KEY), any(Duration.class))).willReturn(Mono.just(true));
        given(redisRepository.unlock(LOCK_KEY)).willReturn(Mono.just(1L));

        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectNextMatches(entity -> entity.id().equals(COUPON_ID))
                .verifyComplete();

        verify(couponIssueService, never()).findCoupon(anyLong()); // DB 조회 없음
        verify(redisRepository, never()).set(any(), any(), any());  // Redis 저장 없음
    }

    @Test
    @DisplayName("락 획득 실패 - 대기 후 Redis 재조회 성공")
    void lockAcquireFail_waitsAndRetryFromRedis() throws Exception {
        String json = objectMapper.writeValueAsString(sampleEntity);

        // 첫 번째 get: 미스 → 락 실패 → 대기 후 두 번째 get: 히트
        given(redisRepository.get(CACHE_KEY))
                .willReturn(Mono.empty())
                .willReturn(Mono.just(json));
        given(redisRepository.tryLock(eq(LOCK_KEY), any(Duration.class))).willReturn(Mono.just(false));

        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectNextMatches(entity -> entity.id().equals(COUPON_ID))
                .verifyComplete();

        verify(couponIssueService, never()).findCoupon(anyLong());
        verify(redisRepository, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("DB 조회 실패 시 에러 전파 및 락 해제")
    void dbLoadFail_propagatesError_andUnlocks() {
        given(redisRepository.get(CACHE_KEY)).willReturn(Mono.empty());
        given(redisRepository.tryLock(eq(LOCK_KEY), any(Duration.class))).willReturn(Mono.just(true));
        given(couponIssueService.findCoupon(COUPON_ID)).willThrow(new RuntimeException("DB 연결 실패"));
        given(redisRepository.unlock(LOCK_KEY)).willReturn(Mono.just(1L));

        StepVerifier.create(couponCacheService.getCouponCache(COUPON_ID))
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && e.getMessage().contains("DB 연결 실패"))
                .verify();

        verify(redisRepository).unlock(LOCK_KEY); // 에러 발생해도 락 해제 확인
    }
}
