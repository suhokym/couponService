package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.repository.RedisRepository;
import com.eCommerce.couponApi.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponApi.repository.redisDto.CouponRedisEntity;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.service.CouponIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    private CouponIssueService couponIssueService;

    private static final long COUPON_ID = 1L;
    private static final String USER_ID = "user1";

    private CouponRedisEntity firstComeCoupon;
    private CouponRedisEntity openCoupon;

    @BeforeEach
    void setUp() {
        firstComeCoupon = new CouponRedisEntity(
                COUPON_ID,
                CampaignType.FIRST_COME,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                CampaignStatus.ACTIVE
        );
        openCoupon = new CouponRedisEntity(
                COUPON_ID,
                CampaignType.OPEN,
                null, // OPEN은 수량 무제한
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                CampaignStatus.ACTIVE
        );
    }

    // ══════════════════════════════════════════════════════════════
    // 선착순(FIRST_COME) 발급
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("선착순(FIRST_COME) 발급")
    class FirstComeIssue {

        @Test
        @DisplayName("발급 성공: SUCCESS_FIRST_COME 반환, requestId 포함")
        void success_returnsSuccessCodeAndRequestId() {
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(firstComeCoupon));
            given(redisRepository.issueRequest(COUPON_ID, USER_ID, 100))
                    .willReturn(Mono.just(CouponIssueRequestCode.SUCCESS_FIRST_COME));
            given(couponIssueService.saveIssueRequestAndEventLog(COUPON_ID, USER_ID, "first-coupon-issue-requested"))
                    .willReturn(1L);

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectNextMatches(result ->
                            result.code() == CouponIssueRequestCode.SUCCESS_FIRST_COME
                            && result.requestId() == 1L)
                    .verifyComplete();

            verify(couponIssueService).saveIssueRequestAndEventLog(COUPON_ID, USER_ID, "first-coupon-issue-requested");
        }

        @Test
        @DisplayName("중복 발급: CouponIssueException 발생, DB 저장 안 됨")
        void duplicate_throwsException_noDbSave() {
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(firstComeCoupon));
            given(redisRepository.issueRequest(COUPON_ID, USER_ID, 100))
                    .willReturn(Mono.just(CouponIssueRequestCode.DUPLICATE_COUPON_ISSUE));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(CouponIssueException.class)
                    .verify();

            verify(couponIssueService, never()).saveIssueRequestAndEventLog(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("수량 초과: CouponIssueException 발생, DB 저장 안 됨")
        void soldOut_throwsException_noDbSave() {
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(firstComeCoupon));
            given(redisRepository.issueRequest(COUPON_ID, USER_ID, 100))
                    .willReturn(Mono.just(CouponIssueRequestCode.INVALID_COUPON_ISSUE_QUANTITY));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(CouponIssueException.class)
                    .verify();

            verify(couponIssueService, never()).saveIssueRequestAndEventLog(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("DB 저장 실패: 예외 전파")
        void dbFail_propagatesError() {
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(firstComeCoupon));
            given(redisRepository.issueRequest(COUPON_ID, USER_ID, 100))
                    .willReturn(Mono.just(CouponIssueRequestCode.SUCCESS_FIRST_COME));
            given(couponIssueService.saveIssueRequestAndEventLog(COUPON_ID, USER_ID, "first-coupon-issue-requested"))
                    .willThrow(new RuntimeException("DB 연결 실패"));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 오픈(OPEN) 발급
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("오픈(OPEN) 발급")
    class OpenIssue {

        @Test
        @DisplayName("발급 성공: SUCCESS_OPEN 반환, Redis 수량 체크 없음")
        void success_returnsSuccessOpenCode_noRedisCheck() {
            // ⚠️ NOTE: OPEN은 Redis issueRequest 없이 바로 DB 저장
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(openCoupon));
            given(couponIssueService.saveIssueRequestAndEventLog(COUPON_ID, USER_ID, "open-coupon-issue-requested"))
                    .willReturn(2L);

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectNextMatches(result ->
                            result.code() == CouponIssueRequestCode.SUCCESS_OPEN
                            && result.requestId() == 2L)
                    .verifyComplete();

            // ⚠️ NOTE: issueRequest 3번째 파라미터가 int(primitive)이므로 any() 대신 anyInt() 사용
            verify(redisRepository, never()).issueRequest(anyLong(), anyString(), anyInt());
        }

        @Test
        @DisplayName("DB 저장 실패: 예외 전파")
        void dbFail_propagatesError() {
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(openCoupon));
            given(couponIssueService.saveIssueRequestAndEventLog(COUPON_ID, USER_ID, "open-coupon-issue-requested"))
                    .willThrow(new RuntimeException("DB 연결 실패"));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 캠페인 상태 검증 (availableIssueableCoupon)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("캠페인 상태 검증")
    class CampaignValidation {

        @Test
        @DisplayName("MANUAL 타입 → CouponIssueException 발생")
        void manualType_throwsException() {
            CouponRedisEntity manualCoupon = new CouponRedisEntity(
                    COUPON_ID, CampaignType.MANUAL, null,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    CampaignStatus.ACTIVE
            );
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(manualCoupon));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(CouponIssueException.class)
                    .verify();
        }

        @Test
        @DisplayName("종료된 캠페인(ENDED) → CouponIssueException 발생")
        void endedCampaign_throwsException() {
            CouponRedisEntity endedCoupon = new CouponRedisEntity(
                    COUPON_ID, CampaignType.FIRST_COME, 100,
                    LocalDateTime.now().minusDays(10),
                    LocalDateTime.now().minusDays(1),
                    CampaignStatus.ENDED
            );
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(endedCoupon));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(CouponIssueException.class)
                    .verify();
        }

        @Test
        @DisplayName("비활성 캠페인(INACTIVE) → CouponIssueException 발생")
        void inactiveCampaign_throwsException() {
            CouponRedisEntity inactiveCoupon = new CouponRedisEntity(
                    COUPON_ID, CampaignType.FIRST_COME, 100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1),
                    CampaignStatus.INACTIVE
            );
            given(couponCacheService.getCouponCache(COUPON_ID)).willReturn(Mono.just(inactiveCoupon));

            StepVerifier.create(couponRedisService.issueCoupon(COUPON_ID, USER_ID))
                    .expectError(CouponIssueException.class)
                    .verify();
        }
    }
}
