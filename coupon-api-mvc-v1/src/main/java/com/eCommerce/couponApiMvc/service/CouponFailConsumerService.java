package com.eCommerce.couponApiMvc.service;

import com.eCommerce.couponApiMvc.repository.RedisRepository;
import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CouponFailConsumerService {

    private final RedisRepository redisRepository;


    @KafkaListener(topics = "coupon-issue-all-fail", groupId = "coupon-group-all-fail")
    public void issueAllfail(CouponIssueRetryEventDto retryEvent) {
        //redis에 있는 userId 삭제
        redisRepository.removeMember();
    }

}
