package com.eCommerce.couponApi.service;

import com.eCommerce.couponApi.dto.CouponIssueKafkaDto;
import com.eCommerce.couponApi.dto.CouponIssueRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CouponEventProducer {


        private final KafkaTemplate<String, CouponIssueKafkaDto> kafkaTemplate;



    public void publishIssuedRequest(CouponIssueKafkaDto event) {

            kafkaTemplate.send("coupon-issue-requested", String.valueOf(event.couponId()), event);





        }




}
