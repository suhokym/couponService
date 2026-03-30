package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponIssueRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class IssueRequestAdminServiceTest {

    @InjectMocks
    private IssueRequestAdminService service;

    @Mock
    private CouponIssueRequestRepository issueRequestRepository;

    private CouponCampaign campaign;
    private CouponIssueRequest requestedIssue;
    private CouponIssueRequest issuedIssue;

    @BeforeEach
    void setUp() {
        campaign = CouponCampaign.builder()
                .couponId(1L)
                .name("테스트 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .IssuedQuantity(0)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        requestedIssue = CouponIssueRequest.builder()
                .userId("user1")
                .campaign(campaign)
                .status(IssueRequestStatus.REQUESTED)
                .build();

        issuedIssue = CouponIssueRequest.builder()
                .userId("user2")
                .campaign(campaign)
                .status(IssueRequestStatus.ISSUED)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // findAll()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("status null — 전체 목록 반환")
        void nullStatus_returnsAll() {
            given(issueRequestRepository.findAll()).willReturn(List.of(requestedIssue, issuedIssue));

            List<CouponIssueRequestDto> result = service.findAll(null);

            assertThat(result).hasSize(2);
            verify(issueRequestRepository).findAll();
            verify(issueRequestRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("status ISSUED — ISSUED 목록만 반환")
        void issuedStatus_returnsFiltered() {
            given(issueRequestRepository.findByStatus(IssueRequestStatus.ISSUED))
                    .willReturn(List.of(issuedIssue));

            List<CouponIssueRequestDto> result = service.findAll(IssueRequestStatus.ISSUED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(IssueRequestStatus.ISSUED);
            verify(issueRequestRepository, never()).findAll();
        }

        @Test
        @DisplayName("status REQUESTED — REQUESTED 목록만 반환")
        void requestedStatus_returnsFiltered() {
            given(issueRequestRepository.findByStatus(IssueRequestStatus.REQUESTED))
                    .willReturn(List.of(requestedIssue));

            List<CouponIssueRequestDto> result = service.findAll(IssueRequestStatus.REQUESTED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(IssueRequestStatus.REQUESTED);
        }

        @Test
        @DisplayName("필터 결과 없음 — 빈 리스트 반환")
        void filteredEmpty_returnsEmptyList() {
            given(issueRequestRepository.findByStatus(IssueRequestStatus.FAILED_FATAL))
                    .willReturn(List.of());

            List<CouponIssueRequestDto> result = service.findAll(IssueRequestStatus.FAILED_FATAL);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // findById()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("존재하는 requestId — DTO 반환")
        void found_returnsDto() {
            given(issueRequestRepository.findById(1L)).willReturn(Optional.of(requestedIssue));

            CouponIssueRequestDto result = service.findById(1L);

            assertThat(result.userId()).isEqualTo("user1");
            assertThat(result.status()).isEqualTo(IssueRequestStatus.REQUESTED);
        }

        @Test
        @DisplayName("존재하지 않는 requestId → COUPON_ISSUE_REQUEST_NOT_FOUND 예외")
        void notFound_throwsNotFound() {
            given(issueRequestRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99L))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND);
        }
    }
}
