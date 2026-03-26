package com.eCommerce.couponAdmin.dto;

import com.eCommerce.couponDomain.entity.enums.CampaignStatus;

public record CampaignStatusUpdateRequest(CampaignStatus status) {}
