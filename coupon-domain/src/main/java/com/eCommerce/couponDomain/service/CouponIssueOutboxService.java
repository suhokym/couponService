package com.eCommerce.couponDomain.service;

import com.eCommerce.couponDomain.dto.CouponIssueEventDto;
import com.eCommerce.couponDomain.entity.CouponCampaign;
import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.CouponIssueRequest;
import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.eCommerce.couponDomain.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CouponIssueOutboxService {

    private final OutboxEventRepository outboxEventRepository;


    // ② 스케줄러에서 호출 - 발행할 이벤트 조회
    @Transactional(readOnly = true)
    public List<OutboxEvent> findPendingOutbox() {
        return outboxEventRepository.findByPublishStatus(OutboxPublishStatus.PENDING);
    }

    // ③ 스케줄러에서 호출 - 재시도할 이벤트 조회 (3회 미만)
    @Transactional(readOnly = true)
    public List<OutboxEvent> findRetryableFailedOutbox() {
        return outboxEventRepository.findByPublishStatusAndRetryCountLessThan(
                OutboxPublishStatus.FAILED, 3
        );
    }

    // ④ Kafka 발행 성공 시
    @Transactional
    public void markPublished(Long outboxId) {
        OutboxEvent event = outboxEventRepository.findById(outboxId)
                .orElseThrow(() -> new CouponIssueException(ErrorCode.OUTBOX_NOT_EXIST, "아웃박스를 찾을 수 없습니다."));
        event.markPublished();
    }

    // ⑤ Kafka 발행 실패 시
    @Transactional
    public void markFailed(Long outboxId) {
        OutboxEvent event = outboxEventRepository.findById(outboxId)
                .orElseThrow(() -> new CouponIssueException(ErrorCode.OUTBOX_NOT_EXIST, "아웃박스를 찾을 수 없습니다."));
        event.markFailed();
    }

}
