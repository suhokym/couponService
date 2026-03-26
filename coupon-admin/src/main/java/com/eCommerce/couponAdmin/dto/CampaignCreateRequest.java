package com.eCommerce.couponAdmin.dto;

import com.eCommerce.couponDomain.entity.enums.CampaignType;

import java.time.LocalDateTime;

public record CampaignCreateRequest(
        String name,
        CampaignType type,
        Integer totalQuantity,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}
