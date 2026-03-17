package com.eCommerce.couponConsumer;

import com.eCommerce.couponDomain.CouponDomainConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CouponDomainConfiguration.class)
@SpringBootApplication
public class CouponConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponConsumerApplication.class, args);
    }
}
