package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.CouponEventLog;
import com.eCommerce.couponDomain.entity.enums.EventProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponEventLogRepository extends JpaRepository<CouponEventLog, Long> {

    // OneToMany 전환 후 — 시도 순서대로 전체 이력 조회
    List<CouponEventLog> findByRequest_RequestIdOrderByCreatedAtAsc(Long requestId);

    // 멱등성 체크 — SUCCESS 로그가 존재하면 이미 처리 완료된 요청
    boolean existsByRequest_RequestIdAndProcessingStatus(Long requestId, EventProcessingStatus status);

    // 최신 로그 1건 — eventType/payload 재사용 및 EventLog 존재 여부 확인에 사용
    Optional<CouponEventLog> findTopByRequest_RequestIdOrderByCreatedAtDesc(Long requestId);

}
