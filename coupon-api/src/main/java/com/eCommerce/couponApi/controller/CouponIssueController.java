package com.eCommerce.couponApi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

    @PostMapping("/issue")
    public Mono<?> CouponIssue(){
        //쿠폰 발급 redis에 저장까지 선착순일 경우에만 redis lua script로 중복관리 선착순 관리만
        return Mono.empty();
    }

}
