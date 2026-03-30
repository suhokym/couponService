package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.service.UserCouponAdminService;
import com.eCommerce.couponDomain.dto.UserCouponDto;
import com.eCommerce.couponDomain.entity.enums.UserCouponStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserCouponAdminControllerTest {

    @InjectMocks
    private UserCouponAdminController controller;

    @Mock
    private UserCouponAdminService userCouponAdminService;

    private MockMvc mockMvc;
    private UserCouponDto user1Dto;
    private UserCouponDto user2Dto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        user1Dto = new UserCouponDto(
                1L, "user1", 10L, "CODE-AAAA",
                UserCouponStatus.ISSUED, null, null, null,
                LocalDateTime.now().plusDays(30), null, null);

        user2Dto = new UserCouponDto(
                2L, "user2", 10L, "CODE-BBBB",
                UserCouponStatus.USED, null, null, LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(30), null, null);
    }

    // ══════════════════════════════════════════════════════════════
    // GET /admin/user-coupons
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /admin/user-coupons")
    class FindAll {

        @Test
        @DisplayName("userId 파라미터 없음 — 전체 목록 200")
        void noUserId_returns200WithAll() throws Exception {
            given(userCouponAdminService.findAll(null)).willReturn(List.of(user1Dto, user2Dto));

            mockMvc.perform(get("/admin/user-coupons"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("userId=user1 — user1 쿠폰만 반환 200")
        void withUserId_returnsFiltered() throws Exception {
            given(userCouponAdminService.findAll("user1")).willReturn(List.of(user1Dto));

            mockMvc.perform(get("/admin/user-coupons").param("userId", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").value("user1"))
                    .andExpect(jsonPath("$[0].couponCode").value("CODE-AAAA"));
        }

        @Test
        @DisplayName("userId로 조회 결과 없음 — 200 + 빈 배열")
        void noResult_returns200EmptyArray() throws Exception {
            given(userCouponAdminService.findAll("unknown")).willReturn(List.of());

            mockMvc.perform(get("/admin/user-coupons").param("userId", "unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("쿠폰 상태 USED 정확히 직렬화")
        void statusSerializedCorrectly() throws Exception {
            given(userCouponAdminService.findAll("user2")).willReturn(List.of(user2Dto));

            mockMvc.perform(get("/admin/user-coupons").param("userId", "user2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("USED"));
        }
    }
}
