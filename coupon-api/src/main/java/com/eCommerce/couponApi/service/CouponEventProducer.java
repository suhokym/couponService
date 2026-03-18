package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.dto.CouponIssueReqeustDto;
import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class CouponEventProducer {


        private final KafkaTemplate<String, CouponIssueReqeustDto> kafkaTemplate;

        public void publishIssuedRequest(CouponIssueReqeustDto event) {
            kafkaTemplate.send("coupon-issue-requested", String.valueOf(event.couponId()), event);
        }




}
