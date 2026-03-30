package com.eCommerce.couponDomain.entity;

import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 발급 요청 엔티티
 * - 사용자의 쿠폰 발급 요청을 추적하며, Kafka를 통한 비동기 처리 상태를 관리
 * - 실패 시 failReason과 retryCount를 통해 재시도 여부를 결정
 */
@Entity
@Table(name = "coupon_issue_request")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponIssueRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @Column(nullable = false)
    private String userId; // 발급 요청 사용자 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CouponCampaign campaign; // 발급 대상 캠페인

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueRequestStatus status; // 요청 처리 상태

    private String failReason; // 실패 사유 (실패 시에만 기록)

    @Column(nullable = false)
    private int retryCount = 0; // 재시도 횟수

    public void updateStatus(IssueRequestStatus status) {
        this.status = status;
    }

    public void updateRetryCount(String failReason) {
        this.status = IssueRequestStatus.RETRYING;
        this.retryCount = this.retryCount+1;
        this.failReason = failReason;
    }
    public void updateAllFail(String failReason) {
        this.failReason = failReason;
        this.status = IssueRequestStatus.FAILED_FATAL;
    }
    public void faliedBusiness(String failReason) {
        this.failReason = failReason;
        this.status = IssueRequestStatus.FAILED_FATAL;
    }

}
