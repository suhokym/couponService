package com.eCommerce.couponConsumer.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.UserCoupon;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.entity.enums.UserCouponStatus;
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
        log.info("수신된 메시지: {}", event);
        couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.PROCESSING);
        CouponCampaign campaign = couponIssueService.findCoupon(event.couponId());
        couponIssueService.checkAlreadyEvent(event.couponIssueRequestId());
        //실제 유저 쿠폰 있는지 확인 //user coupon쪽 엔티티 동작하는법 고유 쿠폰 코드 확인
        try {
            couponIssueService.saveUserCoupon(UserCoupon.builder()
                    .userId(event.userId())
                    .campaign(campaign)
                    .status(UserCouponStatus.ISSUED)
                    .expiredAt(campaign.getEndAt())
                    .build());
            couponIssueService.updateCouponEventLog(event.couponIssueRequestId(), EventProcessingStatus.SUCCESS);
            couponIssueService.updateIssueRequestStatus(event.couponIssueRequestId(), IssueRequestStatus.ISSUED);
        }catch (Exception e){
            log.error("발급 실패", e.getMessage());
        }

        //여기서 실제 발급 처리
        //    -user coupon 저장 request,event상태 변경 처리성공 변경
        //1. 실제 유저 쿠폰이 있는 경우 익셉션아님 그냥 실패처리
        //    request,event상태 변경 처리성공 변경
        //2. 일시적 오류시 익셉션 처리
        //모든게 성공이 됐다면 kafka 성공 토픽에 저장

        //2. eventlog에 저장
        // 2. → issue_request 테이블에 저장 (상태: PENDING)
        // 3. → Kafka 발행 [coupon-issue-requested]
        // 4. → Consumer가 수신 → 실제 쿠폰 발급 DB 저장
        // 5. → issue_request 상태 업데이트 (PENDING → COMPLETED)
    }








}
