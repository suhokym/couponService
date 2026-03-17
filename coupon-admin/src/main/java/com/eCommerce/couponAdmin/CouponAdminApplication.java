package com.eCommerce.couponAdmin;

import com.eCommerce.couponDomain.CouponDomainConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CouponDomainConfiguration.class)
@EntityScan(basePackages = "com.eCommerce.couponDomain.entity")
public class CouponAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponAdminApplication.class, args);
    }
}
