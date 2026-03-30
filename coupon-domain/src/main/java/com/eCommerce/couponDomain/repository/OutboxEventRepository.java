package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.OutboxEvent;
import com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;


public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {


    // PENDING: 아직 발행 안 된 이벤트
    List<OutboxEvent> findByPublishStatus(OutboxPublishStatus publishStatus);

    // FAILED + retryCount 조건: 3회 미만만 재시도
    List<OutboxEvent> findByPublishStatusAndRetryCountLessThan(
            OutboxPublishStatus publishStatus, int retryCount
    );

    Optional<OutboxEvent> findByAggregateId(Long aggregateId);
}
