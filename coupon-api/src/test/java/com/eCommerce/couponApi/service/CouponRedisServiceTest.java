package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CouponRedisServiceTest {

    @InjectMocks
    private CouponRedisService couponRedisService;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private RedisRepository redisRepository;

    private CouponRedisEntity firstComeCoupon;
    private CouponRedisEntity openCoupon;

    @BeforeEach
    void setUp() {
        firstComeCoupon = new CouponRedisEntity(
                1L,
                CampaignType.FIRST_COME,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                CampaignStatus.ACTIVE
        );

        openCoupon = new CouponRedisEntity(
                2L,
                CampaignType.OPEN,
                null,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                CampaignStatus.ACTIVE
        );
    }

    // ========== 선착순 쿠폰 ==========

    @Test
    @DisplayName("선착순 쿠폰 발급 성공")
    void firstCome_issue_success() {
        // given
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(firstComeCoupon));
        given(redisRepository.issueRequest(1L, "user1", 100))
                .willReturn(Mono.just(CouponIssueReqeustCode.SUCCESS));

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectNext(CouponIssueReqeustCode.SUCCESS)
                .verifyComplete();
    }

    @Test
    @DisplayName("선착순 쿠폰 중복 발급 시 예외")
    void firstCome_issue_duplicate() {
        // given
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(firstComeCoupon));
        given(redisRepository.issueRequest(1L, "user1", 100))
                .willReturn(Mono.just(CouponIssueReqeustCode.DUPLICATE_COUPON_ISSUE));

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectError(CouponIssueException.class)
                .verify();
    }

    @Test
    @DisplayName("선착순 쿠폰 재고 소진 시 예외")
    void firstCome_issue_soldOut() {
        // given
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(firstComeCoupon));
        given(redisRepository.issueRequest(1L, "user1", 100))
                .willReturn(Mono.just(CouponIssueReqeustCode.INVALID_COUPON_ISSUE_QUANTITY));

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectError(CouponIssueException.class)
                .verify();
    }

    // ========== 일반 쿠폰 ==========

    @Test
    @DisplayName("일반 쿠폰 발급 성공")
    void open_issue_success() {
        // given
        given(couponCacheService.getCouponCache(2L))
                .willReturn(Mono.just(openCoupon));
        given(redisRepository.sIsMember(anyString(), eq("user1")))
                .willReturn(Mono.just(false));
        given(redisRepository.sAdd(anyString(), eq("user1")))
                .willReturn(Mono.just(1L));

        // when & then
        StepVerifier.create(couponRedisService.issue(2L, "user1"))
                .expectNext(CouponIssueReqeustCode.SUCCESS)
                .verifyComplete();
    }

    @Test
    @DisplayName("일반 쿠폰 중복 발급 시 예외")
    void open_issue_duplicate() {
        // given
        given(couponCacheService.getCouponCache(2L))
                .willReturn(Mono.just(openCoupon));
        given(redisRepository.sIsMember(anyString(), eq("user1")))
                .willReturn(Mono.just(true));

        // when & then
        StepVerifier.create(couponRedisService.issue(2L, "user1"))
                .expectError(CouponIssueException.class)
                .verify();
    }

    // ========== 공통 예외 ==========

    @Test
    @DisplayName("만료된 캠페인 발급 시 예외")
    void issue_expiredCampaign() {
        // given
        CouponRedisEntity expiredCoupon = new CouponRedisEntity(
                1L,
                CampaignType.FIRST_COME,
                100,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1), // 이미 만료
                CampaignStatus.ENDED
        );
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(expiredCoupon));

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectError(CouponIssueException.class)
                .verify();
    }

    @Test
    @DisplayName("비활성 캠페인 발급 시 예외")
    void issue_inactiveCampaign() {
        // given
        CouponRedisEntity inactiveCoupon = new CouponRedisEntity(
                1L,
                CampaignType.FIRST_COME,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                CampaignStatus.INACTIVE
        );
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(inactiveCoupon));

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectError(CouponIssueException.class)
                .verify();
    }
}
