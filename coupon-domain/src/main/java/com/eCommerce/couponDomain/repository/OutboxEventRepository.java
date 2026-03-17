package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
