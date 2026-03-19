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
    private final CouponIssueService couponIssueService;
    private final ObjectMapper objectMapper;



    //아웃박스 저장하는 로직 스케쥴러는 consumer에서 받음
    public void saveIssueRequestWithOutbox(CouponIssueEventDto event, CouponCampaign campaign) {


        couponIssueService.checkAlreadyRequested(event); //이미 발급된 경우 throw

            CouponIssueRequest issueRequest = CouponIssueRequest
                    .builder()
                    .userId(event.userId())
                    .campaign(campaign)
                    .status(IssueRequestStatus.REQUESTED)
                    .build();
        couponIssueService.saveCouponIssueRequest(issueRequest);//request저장

        try {
            couponIssueService.saveCouponEventLog(
                    CouponEventLog
                            .builder()
                            .request(issueRequest)
                            .payload(objectMapper.writeValueAsString(event))
                            .processingStatus(EventProcessingStatus.PROGRESS).build());//eventlog저장
        } catch (JsonProcessingException e) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,"이벤트 로그에 저장을 실패했습니다 issueRequest: %s".formatted(issueRequest.getRequestId()));
        }


        saveOutbox(
                OutboxEvent
                        .builder()
                        .aggregateType("CouponIssueRequest")
                        .aggregateId(issueRequest.getRequestId())
                        .eventType("COUPON_ISSUED")
                        .build());//outbox저장



    }

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
