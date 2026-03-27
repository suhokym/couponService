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
    public void saveUserCoupon(UserCoupon event) {
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
        CouponEventLog request = couponEventLogRepository
                .findById(issueRequestId)
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

        couponIssueRequest.updateRetryCount();

        Optional<CouponEventLog> byRequestRequestId = couponEventLogRepository.findByRequest_RequestId(couponIssueRequest.getRequestId());
        CouponEventLog couponEventLog = byRequestRequestId.orElseThrow(() ->
                new CouponIssueException(
                        ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,
                        "존재하지 않는 발급요청 이벤트 입니다 requestId: %d".formatted(IssueRequestId)));
        couponEventLog.updateRetryStatus();
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

        Optional<CouponEventLog> byRequestRequestId = couponEventLogRepository.findByRequest_RequestId(couponIssueRequest.getRequestId());
        CouponEventLog couponEventLog = byRequestRequestId.orElseThrow(() ->
                new CouponIssueException(
                        ErrorCode.FAIL_COUPON_EVENT_LOG_ISSUE,
                        "존재하지 않는 발급요청 이벤트 입니다 requestId: %d".formatted(IssueRequestId)));
        couponEventLog.updatefailedStatus();

    }

    @Transactional(readOnly = true)
    public void checkAlreadyEvent(Long issueRequestId) {
        Optional<CouponEventLog> EventByRequestId = couponEventLogRepository.findByRequest_RequestId(issueRequestId);
        if(EventByRequestId.isEmpty()){
            throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "이미 쿠폰 이벤트 발급이 되었습니다 issueRequestId=%d"
                    .formatted(issueRequestId));
        }


    }


}
