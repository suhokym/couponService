package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import com.eCommerce.couponDomain.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {


    private final KafkaTemplate<String, CouponIssueEventDto> kafkaTemplate;
    private final CouponIssueService couponIssueService;
    private final CouponIssueOutboxService couponIssueOutboxService;


    @KafkaListener(topics = "coupon-issue-requested", groupId = "coupon-group")
    public void consumeIssueRequest(CouponIssueEventDto event) {

        CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());

        //1. 여기서 실제 발급 처리 아웃박스도 저장
        log.info("수신된 메시지: {}", event);
        couponIssueOutboxService.saveIssueRequestWithOutbox(event, campaign);


        //2. eventlog에 저장
        // 2. → issue_request 테이블에 저장 (상태: PENDING)
        // 3. → Kafka 발행 [coupon-issue-requested]
        // 4. → Consumer가 수신 → 실제 쿠폰 발급 DB 저장
        // 5. → issue_request 상태 업데이트 (PENDING → COMPLETED)
    }

    public void cosumeIssueRetry(){

    }

    public void cosumeIssueComplete(){

    }

    public void cosumeIssueCompleteFailed(){

    }




}
