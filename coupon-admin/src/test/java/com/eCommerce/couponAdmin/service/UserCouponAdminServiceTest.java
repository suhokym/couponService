package com.eCommerce.couponAdmin.service;

import com.eCommerce.couponDomain.dto.UserCouponDto;
import com.eCommerce.couponDomain.entity.UserCoupon;
import com.eCommerce.couponDomain.entity.enums.UserCouponStatus;
import com.eCommerce.couponDomain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserCouponAdminServiceTest {

    @InjectMocks
    private UserCouponAdminService service;

    @Mock
    private UserCouponRepository userCouponRepository;

    private UserCoupon user1Coupon;
    private UserCoupon user2Coupon;

    @BeforeEach
    void setUp() {
        user1Coupon = UserCoupon.builder()
                .userId("user1")
                .couponId(1L)
                .couponCode("CODE-AAAA")
                .status(UserCouponStatus.ISSUED)
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        user2Coupon = UserCoupon.builder()
                .userId("user2")
                .couponId(1L)
                .couponCode("CODE-BBBB")
                .status(UserCouponStatus.USED)
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // findAll()
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("userId null — 전체 목록 반환")
        void nullUserId_returnsAll() {
            given(userCouponRepository.findAll()).willReturn(List.of(user1Coupon, user2Coupon));

            List<UserCouponDto> result = service.findAll(null);

            assertThat(result).hasSize(2);
            verify(userCouponRepository).findAll();
            verify(userCouponRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("userId 빈 문자열 — 전체 목록 반환 (blank 처리)")
        void blankUserId_returnsAll() {
            given(userCouponRepository.findAll()).willReturn(List.of(user1Coupon, user2Coupon));

            List<UserCouponDto> result = service.findAll("   ");

            assertThat(result).hasSize(2);
            verify(userCouponRepository).findAll();
        }

        @Test
        @DisplayName("userId user1 — user1 쿠폰만 반환")
        void specificUserId_returnsFilteredCoupons() {
            given(userCouponRepository.findByUserId("user1")).willReturn(List.of(user1Coupon));

            List<UserCouponDto> result = service.findAll("user1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo("user1");
            assertThat(result.get(0).couponCode()).isEqualTo("CODE-AAAA");
            verify(userCouponRepository, never()).findAll();
        }

        @Test
        @DisplayName("userId로 조회 결과 없음 — 빈 리스트 반환")
        void unknownUserId_returnsEmpty() {
            given(userCouponRepository.findByUserId("unknown")).willReturn(List.of());

            List<UserCouponDto> result = service.findAll("unknown");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DTO 변환 시 status 필드 정확히 매핑")
        void statusMappedCorrectly() {
            given(userCouponRepository.findByUserId("user2")).willReturn(List.of(user2Coupon));

            List<UserCouponDto> result = service.findAll("user2");

            assertThat(result.get(0).status()).isEqualTo(UserCouponStatus.USED);
        }
    }
}
