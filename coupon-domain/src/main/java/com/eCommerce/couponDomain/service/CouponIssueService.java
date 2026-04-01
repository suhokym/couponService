package com.eCommerce.couponDomain.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.*;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CouponIssueService {

    private final CouponCampaignJpaRepository couponCampaignJpaRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponEventLogRepository couponEventLogRepository;
    private final UserCouponRepository userCouponRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;


    //쿠폰 발급


    //쿠폰 정보 조회
    @Transactional(readOnly = true)
    public CouponCampaign findCoupon(long couponId) {
        return couponCampaignJpaRepository.findById(couponId).orElseThrow(() ->{
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST,"존재하지 않는 쿠폰입니다 couponId=%d".formatted(couponId));
        });
    }

    @Transactional
    public void saveCouponIssueRequest(CouponIssueRequest event) {
        // ⚠️ NOTE: DB 제약 위반 전에 명시적으로 필수 필드 누락을 감지하기 위한 사전 검증
        if (event == null || event.getUserId() == null || event.getCampaign() == null || event.getStatus() == null) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST,
                    "쿠폰 발급 요청 객체의 필수 필드가 누락됐습니다");
        }
        try {
            couponIssueRequestRepository.save(event);
        } catch (Exception e) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST, "쿠폰 대기열 발급에 실패했습니다");
        }
    }

    @Transactional
    public void saveUserCoupon(UserCoupon event, OutboxEvent outboxEvent) {
        // ⚠️ NOTE: couponCode는 UNIQUE 제약 포함 — blank 검증도 추가
        if (event == null
                || event.getUserId() == null
                || event.getCampaign() == null
                || event.getCouponCode() == null || event.getCouponCode().isBlank()
                || event.getStatus() == null
                || event.getExpiredAt() == null) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST,
                    "사용자 쿠폰 객체의 필수 필드가 누락됐습니다");
        }
        try {
            userCouponRepository.save(event);
        } catch (Exception e) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST, "쿠폰 발급에 실패했습니다");
        }try{
            outboxEventRepository.save(outboxEvent);
        }catch (Exception e){
            throw new CouponIssueException(ErrorCode.FAIL_OUTBOX_SAVE, "이미 저장된 아웃박스입니다.");
        }
    }

    @Transactional
    public void saveCouponEventLog(CouponEventLog event) {
        // ⚠️ NOTE: eventType은 String이므로 blank까지 검증
        if (event == null
                || event.getRequest() == null
                || event.getEventType() == null || event.getEventType().isBlank()
                || event.getProcessingStatus() == null) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,
                    "이벤트 로그 객체의 필수 필드가 누락됐습니다");
        }
        try {
            couponEventLogRepository.save(event);
        } catch (Exception e) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE, "쿠폰 이벤트 로그 발급에 실패했습니다");
        }
    }


    @Transactional
    public void updateIssueRequestStatus(Long issueRequestId, IssueRequestStatus status) {
       CouponIssueRequest request = couponIssueRequestRepository
               .findById(issueRequestId)
               .orElseThrow(() ->
                       new CouponIssueException(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND, "해당 request가 존재하지 않습니다 : %d".formatted(issueRequestId)));
       request.updateStatus(status);

    }

    @Transactional
    public void updateCouponEventLog(Long issueRequestId, EventProcessingStatus status) {
        // ⚠️ NOTE: findById(issueRequestId)는 eventId(PK)로 조회하므로 issueRequestId와 다를 수 있음
        //          올바른 조회는 request_id(FK) 기준의 findByRequest_RequestId 사용
        CouponEventLog request = couponEventLogRepository
                .findByRequest_RequestId(issueRequestId)
                .orElseThrow(() ->
                        new CouponIssueException(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND, "해당 Event 존재하지 않습니다 : %d".formatted(issueRequestId)));
        request.updateStatus(status);

    }

    @Transactional
    public void updateIssuedQuantity(Long couponId){
        CouponCampaign couponCampaign = couponCampaignJpaRepository.findById(couponId)
                .orElseThrow(() -> new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "해당 쿠폰은 존재하지 않습니다 : %d".formatted(couponId)));
        couponCampaign.updateIssuedQuantity();
    }




    @Transactional
    public Long saveIssueRequestAndEventLog(Long couponId, String userId, String topic) {

        CouponCampaign coupon = findCoupon(couponId);
        coupon.validateIssuable(); // 캠페인 상태·기간·수량 2차 검증 (Redis 캐시 불일치 방어)

        // 중복 체크는 Redis Lua script(SISMEMBER)에서 이미 처리 — DB 조회 생략
        CouponIssueRequest issueRequest = CouponIssueRequest
                .builder()
                .userId(userId)
                .campaign(coupon)
                .status(IssueRequestStatus.REQUESTED)
                .build();
        saveCouponIssueRequest(issueRequest);//request저장
        CouponIssueEventDto couponIssueEventDto = new CouponIssueEventDto(couponId ,userId,issueRequest.getRequestId());
        try {
            saveCouponEventLog(
                    CouponEventLog
                            .builder()
                            .request(issueRequest)
                            .payload(objectMapper.writeValueAsString(couponIssueEventDto))
                            .eventType(topic)
                            .processingStatus(EventProcessingStatus.PROGRESS).build());//eventlog저장
            return issueRequest.getRequestId();
        } catch (
                JsonProcessingException e) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,"이벤트 로그에 저장을 실패했습니다 issueRequest: %s".formatted(issueRequest.getRequestId()));
        }

    }
    @Transactional(readOnly = true)
    public void checkAlreadyIssuedUserCoupon(Long couponId, String userId,Long issueRequestId) {
        if(userCouponRepository.existsByCampaign_CouponIdAndUserId(couponId,userId)){
            failAlreadyHadUserCoupon(issueRequestId);
            throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE, "이미 발급된 쿠폰입니다");
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failAlreadyHadUserCoupon(Long issueRequestId) {
        // 별도 트랜잭션이라 예외 영향 안 받음
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(issueRequestId)
                .orElseThrow(() -> new CouponIssueException(
                        ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND,
                        "존재하지 않는 발급요청 입니다 requestId: %d".formatted(issueRequestId)));
        request.faliedBusiness("이미 발급된 쿠폰입니다.");
    }

    @Transactional
    public boolean UpdateRetry(Long IssueRequestId, String reason) {
        Optional<CouponIssueRequest> issueRequestOptional = couponIssueRequestRepository.findById(IssueRequestId);

        CouponIssueRequest couponIssueRequest = issueRequestOptional
                .orElseThrow(() ->
                        new CouponIssueException(
                                ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND,
                                "존재하지 않는 발급요청 입니다 requestId: %d".formatted(IssueRequestId)));

        //3번의 실패시 allFailed로 이동
        if(couponIssueRequest.getRetryCount() >= 3){
            allFailed(reason,couponIssueRequest.getRequestId());
            return false;
        }

        couponIssueRequest.updateRetryCount(reason);

        // ⚠️ NOTE: EventLog가 없으면 새로 생성 — 경고 후 넘어가면 downstream checkAlreadyEvent()에서
        //          COUPON_ISSUE_REQUEST_NOT_FOUND 예외가 발생해 retryStep1 무한 루프 → allFailed 소진
        //          EventLog를 복원해야 재처리 체인이 정상 진행될 수 있음
        Optional<CouponEventLog> byRequestRequestId = couponEventLogRepository.findByRequest_RequestId(couponIssueRequest.getRequestId());
        if (byRequestRequestId.isEmpty()) {
            log.warn("[UpdateRetry] EventLog 없음, 복원 생성 - requestId: {}", IssueRequestId);
            try {
                CouponIssueEventDto dto = new CouponIssueEventDto(
                        couponIssueRequest.getCampaign().getCouponId(),
                        couponIssueRequest.getUserId(),
                        couponIssueRequest.getRequestId()
                );
                couponEventLogRepository.save(
                        CouponEventLog.builder()
                                .request(couponIssueRequest)
                                .payload(objectMapper.writeValueAsString(dto))
                                .eventType("recovery")
                                .processingStatus(EventProcessingStatus.PROGRESS)
                                .build()
                );
            } catch (JsonProcessingException e) {
                throw new CouponIssueException(ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,
                        "EventLog 복원 실패 requestId: %d".formatted(IssueRequestId));
            }
        } else {
            byRequestRequestId.get().updateRetryStatus();
        }
        return true;

    }

    @Transactional
    public void allFailed(String failReason,Long IssueRequestId) {
        Optional<CouponIssueRequest> issueRequestOptional = couponIssueRequestRepository.findById(IssueRequestId);

        CouponIssueRequest couponIssueRequest = issueRequestOptional
                .orElseThrow(() ->
                        new CouponIssueException(
                                ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND,
                                "존재하지 않는 발급요청 입니다 requestId: %d".formatted(IssueRequestId)));

        couponIssueRequest.updateAllFail(failReason);

        // ⚠️ NOTE: UpdateRetry와 동일한 패턴 — EventLog 없으면 예외 대신 경고 로그
        //          orElseThrow() 사용 시 롤백으로 updateAllFail()까지 되돌아가
        //          status가 고착되는 문제 방지. allFailed는 IssueRequest 상태 변경이 핵심
        Optional<CouponEventLog> byRequestRequestId = couponEventLogRepository.findByRequest_RequestId(couponIssueRequest.getRequestId());
        if (byRequestRequestId.isEmpty()) {
            log.warn("[AllFailed] EventLog 없음, IssueRequest 상태만 ALL_FAILED로 변경 - requestId: {}", IssueRequestId);
        } else {
            byRequestRequestId.get().updatefailedStatus();
        }

    }

    // 5분 이상 REQUESTED에 멈춰있는 stuck 레코드 조회 (Kafka 발행 실패 등)
    // ⚠️ NOTE: REQUESTED stuck은 상태 변경 없이 Kafka 재발행만 수행
    //          PROCESSING과 달리 DB 상태를 바꾸지 않아 중복 처리 위험이 낮음
    @Transactional(readOnly = true)
    public List<CouponIssueRequest> findStuckRequestedRequests(LocalDateTime threshold) {
        return couponIssueRequestRepository
                .findStuckWithCampaign(IssueRequestStatus.REQUESTED, threshold);
    }

    // 5분 이상 PROCESSING에 멈춰있는 stuck 레코드 조회
    // ⚠️ NOTE: JOIN FETCH로 campaign을 즉시 로딩 — 트랜잭션 종료 후 스케줄러에서
    //          campaign 프록시에 접근할 때 LazyInitializationException이 발생하므로
    //          쿼리 시점에 campaign을 함께 로딩하는 findStuckWithCampaign 사용
    @Transactional(readOnly = true)
    public List<CouponIssueRequest> findStuckProcessingRequests(LocalDateTime threshold) {
        return couponIssueRequestRepository
                .findStuckWithCampaign(IssueRequestStatus.PROCESSING, threshold);
    }

    // stuck 레코드 상태를 REQUESTED로 되돌려 재처리 가능하게 함
    @Transactional
    public void resetToRequested(Long requestId) {
        CouponIssueRequest req = couponIssueRequestRepository.findById(requestId)
                .orElseThrow(() -> new CouponIssueException(
                        ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND,
                        "존재하지 않는 발급요청 입니다 requestId: %d".formatted(requestId)));
        req.updateStatus(IssueRequestStatus.REQUESTED);
    }

    @Transactional(readOnly = true)
    public void checkAlreadyEvent(Long issueRequestId) {
        // ⚠️ NOTE: 이벤트 로그가 없으면 유효하지 않은 요청 → NOT_FOUND
        //          이벤트 로그가 있고 SUCCESS이면 이미 처리된 중복 요청 → DUPLICATED
        Optional<CouponEventLog> EventByRequestId = couponEventLogRepository.findByRequest_RequestId(issueRequestId);
        if(EventByRequestId.isEmpty()){
            throw new CouponIssueException(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND, "존재하지 않는 쿠폰 이벤트 요청입니다 issueRequestId=%d"
                    .formatted(issueRequestId));
        }
        if(EventByRequestId.get().getProcessingStatus() == EventProcessingStatus.SUCCESS){
            throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "이미 처리된 쿠폰 이벤트입니다 issueRequestId=%d"
                    .formatted(issueRequestId));
        }
    }


}
