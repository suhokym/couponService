package com.eCommerce.couponDomain.repository;

import com.eCommerce.couponDomain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {


    @Query("select o from OutboxEvent o where o.publishStatus = com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus.PENDING OR o.publishStatus = com.eCommerce.couponDomain.entity.enums.OutboxPublishStatus.FAILED")
    List<OutboxEvent> findOutboxPendingOrFailed();


}
