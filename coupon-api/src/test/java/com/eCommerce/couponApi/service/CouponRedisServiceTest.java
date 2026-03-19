package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.service.CouponIssueService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponRedisServiceTest {

    @InjectMocks
    private CouponRedisService couponRedisService;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private RedisRepository redisRepository;

    @Mock
    private CouponIssueService couponIssueService;  // 추가

    private CouponRedisEntity firstComeCoupon;

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
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 성공 - request 저장 포함")
    void firstCome_issue_success() {
        // given
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(firstComeCoupon));
        given(redisRepository.issueRequest(1L, "user1", 100))
                .willReturn(Mono.just(CouponIssueReqeustCode.SUCCESS));
        given(couponIssueService.saveIssueRequestAndEventLog(1L, "user1", "coupon-issue-requested"))
                .willReturn(1L);

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectNextMatches(result -> result.code() == CouponIssueReqeustCode.SUCCESS
                        && result.requestId() == 1L)
                .verifyComplete();

        verify(couponIssueService).saveIssueRequestAndEventLog(1L, "user1", "coupon-issue-requested");
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

        // 실패 시 저장 안 됨
        verify(couponIssueService, never()).saveIssueRequestAndEventLog(anyLong(), anyString(), anyString());
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

        verify(couponIssueService, never()).saveIssueRequestAndEventLog(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("DB 저장 실패 시 예외 전파")
    void firstCome_issue_dbFail() {
        // given
        given(couponCacheService.getCouponCache(1L))
                .willReturn(Mono.just(firstComeCoupon));
        given(redisRepository.issueRequest(1L, "user1", 100))
                .willReturn(Mono.just(CouponIssueReqeustCode.SUCCESS));
        given(couponIssueService.saveIssueRequestAndEventLog(1L, "user1", "coupon-issue-requested"))
                .willThrow(new RuntimeException("DB 연결 실패"));

        // when & then
        StepVerifier.create(couponRedisService.issue(1L, "user1"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("만료된 캠페인 발급 시 예외")
    void issue_expiredCampaign() {
        // given
        CouponRedisEntity expiredCoupon = new CouponRedisEntity(
                1L,
                CampaignType.FIRST_COME,
                100,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1),
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