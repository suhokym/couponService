package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.service.IssueRequestAdminService;
import com.eCommerce.couponDomain.dto.CouponIssueRequestDto;
import com.eCommerce.couponDomain.entity.enums.IssueRequestStatus;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class IssueRequestAdminControllerTest {

    @InjectMocks
    private IssueRequestAdminController controller;

    @Mock
    private IssueRequestAdminService issueRequestAdminService;

    private MockMvc mockMvc;
    private CouponIssueRequestDto sampleDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        sampleDto = new CouponIssueRequestDto(
                1L, "user1", 10L, IssueRequestStatus.ISSUED, null, 0, null, null);
    }

    // ══════════════════════════════════════════════════════════════
    // GET /admin/issue-requests
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /admin/issue-requests")
    class FindAll {

        @Test
        @DisplayName("status 파라미터 없음 — 전체 목록 200")
        void noStatus_returns200WithAll() throws Exception {
            given(issueRequestAdminService.findAll(null)).willReturn(List.of(sampleDto));

            mockMvc.perform(get("/admin/issue-requests"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").value("user1"));
        }

        @Test
        @DisplayName("status=ISSUED 파라미터 — 필터링된 목록 200")
        void withStatusFilter_returnsFiltered() throws Exception {
            given(issueRequestAdminService.findAll(IssueRequestStatus.ISSUED)).willReturn(List.of(sampleDto));

            mockMvc.perform(get("/admin/issue-requests").param("status", "ISSUED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("ISSUED"));
        }

        @Test
        @DisplayName("결과 없음 — 200 + 빈 배열")
        void noResult_returns200EmptyArray() throws Exception {
            given(issueRequestAdminService.findAll(IssueRequestStatus.FAILED_FATAL)).willReturn(List.of());

            mockMvc.perform(get("/admin/issue-requests").param("status", "FAILED_FATAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GET /admin/issue-requests/{requestId}
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /admin/issue-requests/{requestId}")
    class FindById {

        @Test
        @DisplayName("존재하는 ID — 200 + DTO")
        void found_returns200WithDto() throws Exception {
            given(issueRequestAdminService.findById(1L)).willReturn(sampleDto);

            mockMvc.perform(get("/admin/issue-requests/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(1))
                    .andExpect(jsonPath("$.status").value("ISSUED"));
        }

        @Test
        @DisplayName("존재하지 않는 ID — CouponIssueException 전파")
        void notFound_throwsException() {
            // ⚠️ NOTE: ExceptionHandler 미설정 standaloneSetup에서는 예외가 NestedServletException으로 래핑되어 전파
            given(issueRequestAdminService.findById(99L))
                    .willThrow(new CouponIssueException(ErrorCode.COUPON_ISSUE_REQUEST_NOT_FOUND, "없음"));

            assertThatThrownBy(() -> mockMvc.perform(get("/admin/issue-requests/99")))
                    .hasRootCauseInstanceOf(CouponIssueException.class);
        }
    }
}
