package com.eCommerce.couponApi;

import com.eCommerce.couponDomain.CouponDomainConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CouponDomainConfiguration.class)
@SpringBootApplication
public class CouponApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponApiApplication.class, args);
    }
}
