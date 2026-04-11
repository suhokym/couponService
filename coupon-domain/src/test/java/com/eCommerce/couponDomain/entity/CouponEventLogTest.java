package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponEventLog 엔티티 테스트
 * ⚠️ NOTE: OneToMany 전환 후 상태 변경 메서드가 삭제됨
 *           각 처리 시도마다 새 레코드를 INSERT하는 방식으로 이력 보존
 */
class CouponEventLogTest {

    private CouponIssueRequest request;

    @BeforeEach
    void setUp() {
        CouponCampaign campaign = CouponCampaign.builder()
                .name("테스트 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .IssuedQuantity(0)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        request = CouponIssueRequest.builder()
                .userId("user1")
                .campaign(campaign)
                .status(IssueRequestStatus.PROCESSING)
                .build();
    }

    @Test
    @DisplayName("동일 request에 상태가 다른 다수의 EventLog를 생성할 수 있다")
    void multipleEventLogs_sameRequest_differentStatus() {
        // 최초 처리 로그
        CouponEventLog progressLog = CouponEventLog.builder()
                .request(request)
                .eventType("first-coupon-issue-requested")
                .payload("{\"couponId\":1}")
                .processingStatus(EventProcessingStatus.PROGRESS)
                .build();

        // 재시도 로그
        CouponEventLog retryLog = CouponEventLog.builder()
                .request(request)
                .eventType("first-coupon-issue-requested")
                .payload("{\"couponId\":1}")
                .processingStatus(EventProcessingStatus.RETRYING)
                .errorMessage("DB timeout")
                .build();

        // 성공 로그
        CouponEventLog successLog = CouponEventLog.builder()
                .request(request)
                .eventType("first-coupon-issue-requested")
                .payload("{\"couponId\":1}")
                .processingStatus(EventProcessingStatus.SUCCESS)
                .build();

        List<CouponEventLog> logs = new ArrayList<>();
        logs.add(progressLog);
        logs.add(retryLog);
        logs.add(successLog);

        // 같은 request에 대해 다수 레코드 생성 가능
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).getProcessingStatus()).isEqualTo(EventProcessingStatus.PROGRESS);
        assertThat(logs.get(1).getProcessingStatus()).isEqualTo(EventProcessingStatus.RETRYING);
        assertThat(logs.get(1).getErrorMessage()).isEqualTo("DB timeout");
        assertThat(logs.get(2).getProcessingStatus()).isEqualTo(EventProcessingStatus.SUCCESS);

        // 모든 로그가 동일한 request를 참조
        assertThat(logs).allMatch(log -> log.getRequest() == request);
    }

    @Test
    @DisplayName("EventLog 빌더로 errorMessage를 null로 생성하면 null이다 (정상 처리 로그)")
    void eventLog_noErrorMessage_isNull() {
        CouponEventLog log = CouponEventLog.builder()
                .request(request)
                .eventType("first-coupon-issue-requested")
                .payload("{\"couponId\":1}")
                .processingStatus(EventProcessingStatus.SUCCESS)
                .build();

        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("EventLog 빌더로 errorMessage를 포함하면 조회 가능하다 (실패/재시도 로그)")
    void eventLog_withErrorMessage_returnable() {
        CouponEventLog log = CouponEventLog.builder()
                .request(request)
                .eventType("first-coupon-issue-requested")
                .payload("{\"couponId\":1}")
                .processingStatus(EventProcessingStatus.FAILED)
                .errorMessage("최종 실패: 3회 재시도 소진")
                .build();

        assertThat(log.getErrorMessage()).isEqualTo("최종 실패: 3회 재시도 소진");
        assertThat(log.getProcessingStatus()).isEqualTo(EventProcessingStatus.FAILED);
    }
}
