package com.eCommerce.couponDomain;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableCaching
@EnableJpaAuditing
@ComponentScan(basePackages = "com.eCommerce.couponDomain")
@EnableJpaRepositories(basePackages = "com.eCommerce.couponDomain.repository")
@EntityScan(basePackages = "com.eCommerce.couponDomain.entity")
public class CouponDomainConfiguration {
}
