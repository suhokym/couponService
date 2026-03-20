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
        couponIssueService.checkAlreadyIssuedUserCoupon(event.couponId(), event.userId(), event.couponIssueRequestId());
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
        //    -user coupon 저장 request,event상태 변경 처리성공 변경do
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

/*
⏺ 전체 코드를 분석했습니다. 전반적인 설계는 탄탄하지만 미완성 부분이 있습니다.
  아래에 정리합니다.

  ---
  프로젝트 리뷰: Coupon Service

  ✅ 잘 된 부분

  아키텍처
  - 멀티모듈 구조 (coupon-domain, coupon-api, coupon-consumer, coupon-admin) 가
  명확하게 분리됨
  - Kafka 기반 비동기 이벤트 처리 설계가 적절함
  - Redis Lua 스크립트로 선착순 중복 발급 방지 (원자적 연산) - 잘 설계됨
  - Transactional Outbox 테이블 스키마 설계까지 고려한 점 좋음
  - Docker Compose로 인프라 구성 완료

  도메인 모델
  - CouponIssueRequest 상태 머신 (REQUESTED → PROCESSING → ISSUED / FAILED_*)이
  잘 설계됨
  - UserCoupon에 낙관적 락(version 필드) 적용
  - CouponEventLog로 감사 추적 고려

  ---
  ❌ 문제점 및 미완성

  🔴 심각 (버그 수준)

  ┌───────────────┬──────────────────────┬──────────────────────────────────┐
  │     문제      │         위치         │               설명               │
  ├───────────────┼──────────────────────┼──────────────────────────────────┤
  │ coupon_code   │                      │ UserCoupon 저장 시 coupon_code에 │
  │ 생성 로직     │ KafkaConsumerService │  값을 넣는 코드가 없음 → NOT     │
  │ 없음          │                      │ NULL 제약 위반 발생              │
  ├───────────────┼──────────────────────┼──────────────────────────────────┤
  │               │                      │ catch 블록에서 로그만 찍고       │
  │ Consumer 오류 │ KafkaConsumerService │ CouponIssueRequest /             │
  │  처리 누락    │                      │ CouponEventLog 상태를 FAILED로   │
  │               │                      │ 업데이트 안 함                   │
  ├───────────────┼──────────────────────┼──────────────────────────────────┤
  │ userId 타입   │ DB vs 코드           │ DB는 user_id BIGINT, 코드에서는  │
  │ 불일치        │                      │ String userId 사용               │
  └───────────────┴──────────────────────┴──────────────────────────────────┘

  🟡 미구현

  ┌──────────────────┬─────────────┬────────────────────────────────────────┐
  │       항목       │  현재 상태  │              필요한 작업               │
  ├──────────────────┼─────────────┼────────────────────────────────────────┤
  │ Outbox 발행      │ 테이블만    │ @Scheduled로 PENDING 이벤트 Kafka 발행 │
  │ 스케줄러         │ 있음        │  작업 필요                             │
  ├──────────────────┼─────────────┼────────────────────────────────────────┤
  │ 재시도 스케줄러  │ 필드만 있음 │ FAILED_RETRYABLE 상태 요청 자동 재시도 │
  │                  │             │  작업 필요                             │
  ├──────────────────┼─────────────┼────────────────────────────────────────┤
  │ Admin 모듈       │ 껍데기만    │ 컨트롤러/서비스 전혀 없음              │
  │                  │ 있음        │                                        │
  ├──────────────────┼─────────────┼────────────────────────────────────────┤
  │ 테스트           │ 전부 TODO   │ 단위/통합 테스트 전무                  │
  └──────────────────┴─────────────┴────────────────────────────────────────┘

  🟠 설계 개선 필요

  1. Kafka 토픽명 하드코딩 - "coupon-issue-requested" 가 컨트롤러, 컨슈머에 직접
   박혀있음 → @Value 또는 application.yml 로 추출 필요
  2. Redis TTL 하드코딩 - 서비스 내 Duration.ofHours(1) 직접 기재
  3. 회로 차단기 없음 - Kafka/DB 장애 시 연쇄 실패 방지 로직 없음 (Resilience4j
  고려)
  4. CouponEventLog 업데이트 시각 없음 - created_at만 있고 상태 변경 추적 불가

  ---
  우선순위 정리

  즉시 수정: coupon_code 생성 로직 추가
  즉시 수정: Consumer catch 블록 상태 업데이트
  단기:      Outbox/재시도 스케줄러 구현
  단기:      테스트 작성
  중기:      Admin 모듈 구현

  전체적으로 핵심 흐름(API → Redis → Kafka → Consumer → DB)은 설계가 잘 되어
  있으나, coupon_code 생성 누락과 Consumer 오류 처리 미완성이 실제 운영 시
  즉각적인 버그로 이어질 수 있습니다. 이 두 가지를 먼저 수정하는 것을
  권장합니다.

✻ Cooked for 2m 29s

────────────────────────────────────────────────────────────────────────────────
❯
────────────────────────────────────────────────────────────────────────────────
  ? for shortcuts


*/






}
