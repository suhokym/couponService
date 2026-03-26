package com.eCommerce.couponApiMvc;

import com.eCommerce.couponDomain.CouponDomainConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// ⚠️ NOTE: coupon-domain은 별도 모듈이라 컴포넌트 스캔 범위 밖에 있으므로
//          @Import로 CouponDomainConfiguration을 명시적으로 등록
@Import(CouponDomainConfiguration.class)
@SpringBootApplication
public class CouponApiMvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponApiMvcApplication.class, args);
    }
}
