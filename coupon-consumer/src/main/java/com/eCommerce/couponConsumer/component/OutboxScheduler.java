package com.eCommerce.couponConsumer.component;


import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.service.CouponIssueOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final CouponIssueOutboxService couponIssueOutboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000L, initialDelay = 2000L)
    @Transactional
    public void publishOutbox(){
        List<OutboxEvent> outboxPendingOrFailed =  couponIssueOutboxService.findOutboxPendingOrFailed();

        for(OutboxEvent outboxEvent : outboxPendingOrFailed){

        }

    }

}
