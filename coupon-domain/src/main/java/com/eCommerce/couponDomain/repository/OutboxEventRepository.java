package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {


    Optional<OutboxEvent> findByAggregateId(Long aggregateId);


}
