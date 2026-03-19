package com.eCommerce.couponDomain.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CouponIssueOutboxService {


    private final OutboxEventRepository outboxEventRepository;


    @Transactional(readOnly = true)
    public List<OutboxEvent> findOutboxPendingOrFailed() {
        return outboxEventRepository.findOutboxPendingOrFailed();
    }



    @Transactional
    public void saveOutbox(OutboxEvent event) {
        try {
            outboxEventRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.FAIL_OUTBOX_SAVE, "아웃박스 적재에 실패했습니다");
        }

    }

}
