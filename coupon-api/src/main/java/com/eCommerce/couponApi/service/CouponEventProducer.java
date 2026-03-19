package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.dto.CouponIssueReqeustDto;
import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.service.CouponIssueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class CouponEventProducer {


        private final KafkaTemplate<String, CouponIssueReqeustDto> kafkaTemplate;
        private final CouponIssueService couponIssueService;
        private final ObjectMapper objectMapper;


    public void publishIssuedRequest(CouponIssueReqeustDto event) {

            kafkaTemplate.send("coupon-issue-requested", String.valueOf(event.couponId()), event);


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




        }




}
