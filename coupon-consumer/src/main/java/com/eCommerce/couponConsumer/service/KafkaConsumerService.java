package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponConsumer.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.repository.CouponCampaignJpaRepository;
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


    @KafkaListener(topics = "coupon-issue-requested", groupId = "coupon-group")
    public void consumeIssueRequest(CouponIssueEventDto event) {

        CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());
        //1. issuerequest에 저장
        log.info("수신된 메시지: {}", event);
        try {
            couponIssueService.saveCouponIssueRequest(
                    CouponIssueRequest
                            .builder()
                            .userId(event.userId())
                            .campaign(campaign)
                            .status(IssueRequestStatus.REQUESTED)
                            .build());
            kafkaTemplate.send("coupon-issue-requested-complete", String.valueOf(event.couponId()), event);
        }catch (Exception e){
            couponIssueService.saveCouponIssueRequest(
                    CouponIssueRequest
                            .builder()
                            .userId(event.userId())
                            .campaign(campaign)
                            .status(IssueRequestStatus.FAILED_REQUESTED)
                            .build());
            //retry 메소드로 이동하기
        }


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
