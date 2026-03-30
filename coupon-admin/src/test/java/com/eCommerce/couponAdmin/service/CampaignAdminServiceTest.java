package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponAdmin.dto.CampaignCreateRequest;
import com.eCommerce.couponAdmin.dto.CampaignStatusUpdateRequest;
import com.eCommerce.couponDomain.dto.CouponCampaignDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponCampaignJpaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignAdminServiceTest {

    @InjectMocks
    private CampaignAdminService campaignAdminService;

    @Mock
    private CouponCampaignJpaRepository campaignRepository;

    private CouponCampaign activeCampaign;
    private CouponCampaign inactiveCampaign;

    @BeforeEach
    void setUp() {
        activeCampaign = CouponCampaign.builder()
                .couponId(1L)
                .name("선착순 캠페인")
                .type(CampaignType.FIRST_COME)
                .status(CampaignStatus.ACTIVE)
                .totalQuantity(100)
                .IssuedQuantity(20)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();

        inactiveCampaign = CouponCampaign.builder()
                .couponId(2L)
                .name("비활성 캠페인")
                .type(CampaignType.OPEN)
                .status(CampaignStatus.INACTIVE)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // findAll()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("2개 캠페인 → DTO 리스트 2개 반환")
        void returnsDtoList() {
            given(campaignRepository.findAll()).willReturn(List.of(activeCampaign, inactiveCampaign));

            List<CouponCampaignDto> result = campaignAdminService.findAll();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).couponId()).isEqualTo(1L);
            assertThat(result.get(1).couponId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("캠페인 없음 — 빈 리스트 반환")
        void empty_returnsEmptyList() {
            given(campaignRepository.findAll()).willReturn(List.of());

            List<CouponCampaignDto> result = campaignAdminService.findAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DTO 변환 시 status 필드 정확히 매핑")
        void dtoStatusMappedCorrectly() {
            given(campaignRepository.findAll()).willReturn(List.of(activeCampaign));

            List<CouponCampaignDto> result = campaignAdminService.findAll();

            assertThat(result.get(0).status()).isEqualTo(CampaignStatus.ACTIVE);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // findById()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("존재하는 캠페인 — DTO 반환")
        void found_returnsDto() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));

            CouponCampaignDto result = campaignAdminService.findById(1L);

            assertThat(result.couponId()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("선착순 캠페인");
        }

        @Test
        @DisplayName("존재하지 않는 ID → COUPON_NOT_EXIST 예외")
        void notFound_throwsCouponNotExist() {
            given(campaignRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> campaignAdminService.findById(99L))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_NOT_EXIST);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // create()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("생성 직후 상태는 INACTIVE")
        void created_withInactiveStatus() {
            CampaignCreateRequest request = new CampaignCreateRequest(
                    "신규 캠페인", CampaignType.FIRST_COME, 200,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(30));

            // ⚠️ NOTE: save()가 받은 엔티티를 그대로 반환하도록 설정
            given(campaignRepository.save(any(CouponCampaign.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            CouponCampaignDto result = campaignAdminService.create(request);

            assertThat(result.status()).isEqualTo(CampaignStatus.INACTIVE);
            assertThat(result.name()).isEqualTo("신규 캠페인");
            assertThat(result.totalQuantity()).isEqualTo(200);
        }

        @Test
        @DisplayName("OPEN 타입 — totalQuantity null 허용")
        void openType_nullTotalQuantityAllowed() {
            CampaignCreateRequest request = new CampaignCreateRequest(
                    "오픈 캠페인", CampaignType.OPEN, null,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(30));
            given(campaignRepository.save(any(CouponCampaign.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            CouponCampaignDto result = campaignAdminService.create(request);

            assertThat(result.totalQuantity()).isNull();
            assertThat(result.type()).isEqualTo(CampaignType.OPEN);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateStatus()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("INACTIVE → ACTIVE 상태 변경")
        void inactiveToActive() {
            given(campaignRepository.findById(2L)).willReturn(Optional.of(inactiveCampaign));
            CampaignStatusUpdateRequest request = new CampaignStatusUpdateRequest(CampaignStatus.ACTIVE);

            CouponCampaignDto result = campaignAdminService.updateStatus(2L, request);

            assertThat(result.status()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE → ENDED 상태 변경")
        void activeToEnded() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            CampaignStatusUpdateRequest request = new CampaignStatusUpdateRequest(CampaignStatus.ENDED);

            CouponCampaignDto result = campaignAdminService.updateStatus(1L, request);

            assertThat(result.status()).isEqualTo(CampaignStatus.ENDED);
        }

        @Test
        @DisplayName("존재하지 않는 ID → COUPON_NOT_EXIST 예외")
        void notFound_throwsCouponNotExist() {
            given(campaignRepository.findById(99L)).willReturn(Optional.empty());
            CampaignStatusUpdateRequest request = new CampaignStatusUpdateRequest(CampaignStatus.ACTIVE);

            assertThatThrownBy(() -> campaignAdminService.updateStatus(99L, request))
                    .isInstanceOf(CouponIssueException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COUPON_NOT_EXIST);
        }
    }
}
