package com.eCommerce.couponDomain.service;

import com.eCommerce.couponDomain.entity.*;
import com.eCommerce.couponDomain.entity.enums.*;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CouponIssueServiceTest {

    @InjectMocks
    private CouponIssueService couponIssueService;

    @Mock
    private CouponCampaignJpaRepository couponCampaignJpaRepository;

    @Mock
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Mock
    private CouponEventLogRepository couponEventLogRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final long COUPON_ID = 1L;
    private static final String USER_ID = "user1";
    private static final long REQUEST_ID = 10L;

    private CouponCampaign activeCampaign;

    @BeforeEach
    void setUp() {
        activeCampaign = CouponCampaign.builder()
                .couponId(COUPON_ID)
                .name("선착순 쿠폰")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .IssuedQuantity(50)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // findCoupon()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findCoupon()")
    class FindCoupon {

        @Test
        @DisplayName("존재하는 쿠폰 — 캠페인 반환")
        void found_returnsCampaign() {
            given(couponCampaignJpaRepository.findById(COUPON_ID)).willReturn(Optional.of(activeCampaign));

            CouponCampaign result = couponIssueService.findCoupon(COUPON_ID);

            assertThat(result.getCouponId()).isEqualTo(COUPON_ID);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 → COUPON_NOT_EXIST 예외")
        void notFound_throwsCouponNotExist() {
            given(couponCampaignJpaRepository.findById(COUPON_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponIssueService.findCoupon(COUPON_ID))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_NOT_EXIST);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // saveCouponIssueRequest()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveCouponIssueRequest()")
    class SaveCouponIssueRequest {

        @Test
        @DisplayName("정상 저장 — save() 호출")
        void validRequest_callsSave() {
            CouponIssueRequest request = CouponIssueRequest.builder()
                    .userId(USER_ID)
                    .campaign(activeCampaign)
                    .status(IssueRequestStatus.REQUESTED)
                    .build();

            couponIssueService.saveCouponIssueRequest(request);

            verify(couponIssueRequestRepository).save(request);
        }

        @Test
        @DisplayName("null 요청 → FAIL_COUPON_ISSUE_REQUEST 예외")
        void nullRequest_throwsFail() {
            assertThatThrownBy(() -> couponIssueService.saveCouponIssueRequest(null))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("userId null → FAIL_COUPON_ISSUE_REQUEST 예외")
        void nullUserId_throwsFail() {
            CouponIssueRequest request = CouponIssueRequest.builder()
                    .userId(null)
                    .campaign(activeCampaign)
                    .status(IssueRequestStatus.REQUESTED)
                    .build();

            assertThatThrownBy(() -> couponIssueService.saveCouponIssueRequest(request))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("save() 중 DB 예외 → FAIL_COUPON_ISSUE_REQUEST 예외로 변환")
        void dbException_wrappedAsFail() {
            CouponIssueRequest request = CouponIssueRequest.builder()
                    .userId(USER_ID)
                    .campaign(activeCampaign)
                    .status(IssueRequestStatus.REQUESTED)
                    .build();
            willThrow(new RuntimeException("DB 연결 실패")).given(couponIssueRequestRepository).save(any());

            assertThatThrownBy(() -> couponIssueService.saveCouponIssueRequest(request))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // checkAlreadyIssuedUserCoupon()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkAlreadyIssuedUserCoupon()")
    class CheckAlreadyIssuedUserCoupon {

        @Test
        @DisplayName("발급 이력 없음 — 예외 없음")
        void noDuplicate_noException() {
            // ⚠️ NOTE: existsByCampaign_... false → failAlreadyHadUserCoupon 미호출 → findByRequestId stub 불필요
            given(userCouponRepository.existsByCampaign_CouponIdAndUserId(COUPON_ID, USER_ID))
                    .willReturn(false);

            assertThatCode(() -> couponIssueService.checkAlreadyIssuedUserCoupon(COUPON_ID, USER_ID, REQUEST_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 발급된 쿠폰 → DUPLICATED_COUPON_ISSUE 예외")
        void duplicate_throwsDuplicated() {
            given(userCouponRepository.existsByCampaign_CouponIdAndUserId(COUPON_ID, USER_ID))
                    .willReturn(true);
            given(couponIssueRequestRepository.findByRequestId(REQUEST_ID))
                    .willReturn(Optional.of(CouponIssueRequest.builder()
                            .userId(USER_ID).campaign(activeCampaign)
                            .status(IssueRequestStatus.PROCESSING).build()));

            assertThatThrownBy(() -> couponIssueService.checkAlreadyIssuedUserCoupon(COUPON_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATED_COUPON_ISSUE);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // checkAlreadyEvent()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkAlreadyEvent()")
    class CheckAlreadyEvent {

        @Test
        @DisplayName("이벤트 로그 없음 → COUPON_ISSUE_REQUEST_NOT_FOUND 예외")
        void noEventLog_throwsNotFound() {
            // ⚠️ NOTE: OneToMany 전환 후 exists 쿼리 → top 조회 순서로 변경
            given(couponEventLogRepository.existsByRequest_RequestIdAndProcessingStatus(REQUEST_ID, EventProcessingStatus.SUCCESS))
                    .willReturn(false);
            given(couponEventLogRepository.findTopByRequest_RequestIdOrderByCreatedAtDesc(REQUEST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> couponIssueService.checkAlreadyEvent(REQUEST_ID))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND);
        }

        @Test
        @DisplayName("이벤트 로그 PROGRESS 상태 — 예외 없음 (정상 처리 중)")
        void progressStatus_noException() {
            CouponIssueRequest request = CouponIssueRequest.builder()
                    .userId(USER_ID).campaign(activeCampaign)
                    .status(IssueRequestStatus.PROCESSING).build();
            CouponEventLog log = CouponEventLog.builder()
                    .request(request)
                    .eventType("test")
                    .processingStatus(EventProcessingStatus.PROGRESS)
                    .build();
            // SUCCESS 없고 EventLog는 존재 → 정상 처리 중
            given(couponEventLogRepository.existsByRequest_RequestIdAndProcessingStatus(REQUEST_ID, EventProcessingStatus.SUCCESS))
                    .willReturn(false);
            given(couponEventLogRepository.findTopByRequest_RequestIdOrderByCreatedAtDesc(REQUEST_ID))
                    .willReturn(Optional.of(log));

            assertThatCode(() -> couponIssueService.checkAlreadyEvent(REQUEST_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이벤트 로그 SUCCESS 상태 → DUPLICATED_COUPON_ISSUE_EVENT 예외")
        void successStatus_throwsDuplicated() {
            // SUCCESS exists → 즉시 DUPLICATED 예외, findTop 호출 불필요
            given(couponEventLogRepository.existsByRequest_RequestIdAndProcessingStatus(REQUEST_ID, EventProcessingStatus.SUCCESS))
                    .willReturn(true);

            assertThatThrownBy(() -> couponIssueService.checkAlreadyEvent(REQUEST_ID))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateIssueRequestStatus()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateIssueRequestStatus()")
    class UpdateIssueRequestStatus {

        @Test
        @DisplayName("존재하는 request — 상태 업데이트")
        void found_updatesStatus() {
            CouponIssueRequest request = CouponIssueRequest.builder()
                    .userId(USER_ID).campaign(activeCampaign)
                    .status(IssueRequestStatus.REQUESTED).build();
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

            couponIssueService.updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.PROCESSING);

            assertThat(request.getStatus()).isEqualTo(IssueRequestStatus.PROCESSING);
        }

        @Test
        @DisplayName("존재하지 않는 request → COUPON_ISSUE_REQUEST_NOT_FOUND 예외")
        void notFound_throwsNotFound() {
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponIssueService.updateIssueRequestStatus(REQUEST_ID, IssueRequestStatus.PROCESSING))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateIssuedQuantity()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateIssuedQuantity()")
    class UpdateIssuedQuantity {

        @Test
        @DisplayName("존재하는 쿠폰 — 수량 1 증가")
        void found_incrementsQuantity() {
            given(couponCampaignJpaRepository.findById(COUPON_ID)).willReturn(Optional.of(activeCampaign));
            int before = activeCampaign.getIssuedQuantity();

            couponIssueService.updateIssuedQuantity(COUPON_ID);

            assertThat(activeCampaign.getIssuedQuantity()).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 → COUPON_NOT_EXIST 예외")
        void notFound_throwsCouponNotExist() {
            given(couponCampaignJpaRepository.findById(COUPON_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponIssueService.updateIssuedQuantity(COUPON_ID))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_NOT_EXIST);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // UpdateRetry()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UpdateRetry()")
    class UpdateRetry {

        private CouponIssueRequest retryableRequest;
        private CouponEventLog eventLog;

        @BeforeEach
        void init() {
            retryableRequest = CouponIssueRequest.builder()
                    .userId(USER_ID).campaign(activeCampaign)
                    .status(IssueRequestStatus.REQUESTED).build();
            eventLog = CouponEventLog.builder()
                    .request(retryableRequest).eventType("test").payload("{}")
                    .processingStatus(EventProcessingStatus.PROGRESS).build();
        }

        @Test
        @DisplayName("retryCount < 3 → true 반환, retryCount 증가, 새 RETRYING 로그 저장")
        void canRetry_returnsTrueAndIncrements() {
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(retryableRequest));
            // ⚠️ NOTE: OneToMany 전환 후 findTopByRequest_RequestIdOrderByCreatedAtDesc 사용
            given(couponEventLogRepository.findTopByRequest_RequestIdOrderByCreatedAtDesc(any()))
                    .willReturn(Optional.of(eventLog));

            boolean result = couponIssueService.UpdateRetry(REQUEST_ID, "오류");

            assertThat(result).isTrue();
            assertThat(retryableRequest.getRetryCount()).isEqualTo(1);
            // 새 RETRYING 레코드 INSERT 검증
            verify(couponEventLogRepository).save(any(CouponEventLog.class));
        }

        @Test
        @DisplayName("retryCount >= 3 → false 반환 (allFailed 처리, 새 FAILED 로그 저장)")
        void exceededRetry_returnsFalse() {
            // 3번 재시도 상태로 세팅
            retryableRequest.updateRetryCount("이유1");
            retryableRequest.updateRetryCount("이유2");
            retryableRequest.updateRetryCount("이유3");

            CouponEventLog failedLog = CouponEventLog.builder()
                    .request(retryableRequest).eventType("test").payload("{}")
                    .processingStatus(EventProcessingStatus.RETRYING).build();

            // ⚠️ NOTE: allFailed()도 내부에서 findById를 호출하므로 any()로 두 호출 모두 대응
            given(couponIssueRequestRepository.findById(any())).willReturn(Optional.of(retryableRequest));
            given(couponEventLogRepository.findTopByRequest_RequestIdOrderByCreatedAtDesc(any()))
                    .willReturn(Optional.of(failedLog));

            boolean result = couponIssueService.UpdateRetry(REQUEST_ID, "최종 실패");

            assertThat(result).isFalse();
            // allFailed에서 새 FAILED 레코드 INSERT 검증
            verify(couponEventLogRepository).save(any(CouponEventLog.class));
        }

        @Test
        @DisplayName("존재하지 않는 request → COUPON_ISSUE_REQUEST_NOT_FOUND 예외")
        void notFound_throwsNotFound() {
            given(couponIssueRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponIssueService.UpdateRetry(REQUEST_ID, "오류"))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // saveUserCoupon()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveUserCoupon()")
    class SaveUserCoupon {

        @Test
        @DisplayName("null UserCoupon → FAIL_COUPON_ISSUE_REQUEST 예외")
        void nullUserCoupon_throwsFail() {
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateType("CouponIssueRequest").aggregateId(1L)
                    .eventType("SAVE_USER_COUPON").payload("{}")
                    .publishStatus(OutboxPublishStatus.PENDING).build();

            assertThatThrownBy(() -> couponIssueService.saveUserCoupon(null, outbox))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }

        @Test
        @DisplayName("couponCode 빈 문자열 → FAIL_COUPON_ISSUE_REQUEST 예외")
        void blankCouponCode_throwsFail() {
            UserCoupon userCoupon = UserCoupon.builder()
                    .userId(USER_ID).campaign(activeCampaign)
                    .couponCode("")  // 빈 코드
                    .status(UserCouponStatus.ISSUED)
                    .expiredAt(LocalDateTime.now().plusDays(30))
                    .build();
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateType("CouponIssueRequest").aggregateId(1L)
                    .eventType("SAVE_USER_COUPON").payload("{}")
                    .publishStatus(OutboxPublishStatus.PENDING).build();

            assertThatThrownBy(() -> couponIssueService.saveUserCoupon(userCoupon, outbox))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAIL_COUPON_ISSUE_REQUEST);
        }
    }
}
