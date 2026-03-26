package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponAdmin.dto.CampaignCreateRequest;
import com.eCommerce.couponAdmin.dto.CampaignStatusUpdateRequest;
import com.eCommerce.couponDomain.dto.CouponCampaignDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.CouponCampaignJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignAdminService {

    private final CouponCampaignJpaRepository campaignRepository;

    /**
     * 전체 캠페인 목록 조회
     */
    @Transactional(readOnly = true)
    public List<CouponCampaignDto> findAll() {
        return campaignRepository.findAll()
                .stream()
                .map(CouponCampaignDto::from)
                .toList();
    }

    /**
     * 캠페인 단건 조회
     */
    @Transactional(readOnly = true)
    public CouponCampaignDto findById(Long couponId) {
        CouponCampaign campaign = campaignRepository.findById(couponId)
                .orElseThrow(() -> new CouponIssueException(
                        ErrorCode.COUPON_NOT_EXIST,
                        "존재하지 않는 캠페인입니다. couponId=%d".formatted(couponId)));
        return CouponCampaignDto.from(campaign);
    }

    /**
     * 캠페인 생성
     * - 초기 상태는 INACTIVE (관리자가 직접 ACTIVE로 변경)
     */
    @Transactional
    public CouponCampaignDto create(CampaignCreateRequest request) {
        CouponCampaign campaign = CouponCampaign.builder()
                .name(request.name())
                .type(request.type())
                .totalQuantity(request.totalQuantity())
                .status(CampaignStatus.INACTIVE) // 생성 시 비활성 상태로 시작
                .startAt(request.startAt())
                .endAt(request.endAt())
                .build();
        return CouponCampaignDto.from(campaignRepository.save(campaign));
    }

    /**
     * 캠페인 상태 변경 (ACTIVE / INACTIVE / ENDED)
     */
    @Transactional
    public CouponCampaignDto updateStatus(Long couponId, CampaignStatusUpdateRequest request) {
        CouponCampaign campaign = campaignRepository.findById(couponId)
                .orElseThrow(() -> new CouponIssueException(
                        ErrorCode.COUPON_NOT_EXIST,
                        "존재하지 않는 캠페인입니다. couponId=%d".formatted(couponId)));
        // 엔티티의 updateStatus 메서드를 통해 상태 변경 (더티 체킹으로 자동 반영)
        campaign.updateStatus(request.status());
        return CouponCampaignDto.from(campaign);
    }
}
