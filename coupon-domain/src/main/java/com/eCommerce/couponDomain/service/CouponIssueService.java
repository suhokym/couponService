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
        try {
            couponIssueRequestRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 대기열 발급에 실패했습니다");
        }

    }

//    @Transactional(readOnly = true)
//    public CouponIssueRequest findCouponIssueRequest(long requestId) {
//        return couponIssueRequestRepository.findById(requestId);
//    }

    @Transactional
    public void saveUserCoupon(UserCoupon event) {
        try {
            userCouponRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 발급에 실패했습니다");
        }

    }

    @Transactional
    public void saveCouponEventLog(CouponEventLog event) {
        try {
            couponEventLogRepository.save(event);
        }catch (Exception e) {
            throw new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "쿠폰 이벤트 로그 발급에 실패했습니다");
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
    public Long saveIssueRequestAndEventLog(Long couponId, String userId, String topic) {

        CouponCampaign coupon = findCoupon(couponId);

        checkAlreadyRequested(couponId,userId); //이미 발급된 경우 throw

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
    public void checkAlreadyEvent(Long issueRequestId) {
        Optional<CouponEventLog> EventByRequestId = couponEventLogRepository.findByRequest_RequestId(issueRequestId);
        if(EventByRequestId.isEmpty()){
            throw new CouponIssueException(ErrorCode.DUPLICATED_COUPON_ISSUE_EVENT, "이미 쿠폰 이벤트 발급이 되었습니다 issueRequestId=%d"
                    .formatted(issueRequestId));
        }


    }

    @Transactional(readOnly = true)
    //이미 발급된 경우 비즈니스 오류 userCoupon을 기준으로 해야함
    public void checkAlreadyRequested(Long couponId, String userId) {
        CouponIssueRequest issueRequest = couponIssueRequestRepository.findByRequestIdAndUserId(couponId, userId);
        if(issueRequest != null) {
            throw new CouponIssueException(ErrorCode.FAIL_COUPON_ISSUE_REQUEST, "이미 쿠폰 대기열 발급이 되었습니다 couponId=%d userId=%s"
                    .formatted(couponId, userId));

        }

    }


}
