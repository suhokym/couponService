package com.eCommerce.couponAdmin.controller;

import com.eCommerce.couponAdmin.dto.CampaignCreateRequest;
import com.eCommerce.couponAdmin.dto.CampaignStatusUpdateRequest;
import com.eCommerce.couponAdmin.service.CampaignAdminService;
import com.eCommerce.couponDomain.dto.CouponCampaignDto;
import com.eCommerce.couponDomain.entity.enums.CampaignStatus;
import com.eCommerce.couponDomain.entity.enums.CampaignType;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import com.eCommerce.couponDomain.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CampaignAdminControllerTest {

    @InjectMocks
    private CampaignAdminController controller;

    @Mock
    private CampaignAdminService campaignAdminService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private CouponCampaignDto sampleDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        sampleDto = new CouponCampaignDto(
                1L, "선착순 캠페인", 100, 20,
                CampaignType.FIRST_COME, CampaignStatus.ACTIVE,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(2));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /admin/campaigns
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /admin/campaigns")
    class FindAll {

        @Test
        @DisplayName("캠페인 목록 조회 — 200 + JSON 배열")
        void findAll_returns200WithList() throws Exception {
            given(campaignAdminService.findAll()).willReturn(List.of(sampleDto));

            mockMvc.perform(get("/admin/campaigns"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("선착순 캠페인"));
        }

        @Test
        @DisplayName("캠페인 없음 — 200 + 빈 배열")
        void findAll_empty_returns200EmptyArray() throws Exception {
            given(campaignAdminService.findAll()).willReturn(List.of());

            mockMvc.perform(get("/admin/campaigns"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GET /admin/campaigns/{couponId}
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /admin/campaigns/{couponId}")
    class FindById {

        @Test
        @DisplayName("존재하는 ID — 200 + DTO")
        void findById_found_returns200() throws Exception {
            given(campaignAdminService.findById(1L)).willReturn(sampleDto);

            mockMvc.perform(get("/admin/campaigns/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.couponId").value(1))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("존재하지 않는 ID — CouponIssueException 전파")
        void findById_notFound_throwsException() {
            // ⚠️ NOTE: ExceptionHandler 미설정 standaloneSetup에서는 예외가 NestedServletException으로 래핑되어 전파
            given(campaignAdminService.findById(99L))
                    .willThrow(new CouponIssueException(ErrorCode.COUPON_NOT_EXIST, "없음"));

            assertThatThrownBy(() -> mockMvc.perform(get("/admin/campaigns/99")))
                    .hasRootCauseInstanceOf(CouponIssueException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // POST /admin/campaigns
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /admin/campaigns")
    class Create {

        @Test
        @DisplayName("캠페인 생성 — 200 + 생성된 DTO")
        void create_returns200WithDto() throws Exception {
            CampaignCreateRequest request = new CampaignCreateRequest(
                    "신규 캠페인", CampaignType.FIRST_COME, 100,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(30));

            CouponCampaignDto created = new CouponCampaignDto(
                    2L, "신규 캠페인", 100, 0,
                    CampaignType.FIRST_COME, CampaignStatus.INACTIVE,
                    request.startAt(), request.endAt(), null, null);

            given(campaignAdminService.create(any(CampaignCreateRequest.class))).willReturn(created);

            mockMvc.perform(post("/admin/campaigns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("신규 캠페인"))
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PATCH /admin/campaigns/{couponId}/status
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATCH /admin/campaigns/{couponId}/status")
    class UpdateStatus {

        @Test
        @DisplayName("상태 변경 — 200 + 업데이트된 DTO")
        void updateStatus_returns200WithUpdatedDto() throws Exception {
            CampaignStatusUpdateRequest request = new CampaignStatusUpdateRequest(CampaignStatus.ENDED);
            CouponCampaignDto updated = new CouponCampaignDto(
                    1L, "선착순 캠페인", 100, 100,
                    CampaignType.FIRST_COME, CampaignStatus.ENDED,
                    sampleDto.startAt(), sampleDto.endAt(), null, null);

            given(campaignAdminService.updateStatus(eq(1L), any(CampaignStatusUpdateRequest.class)))
                    .willReturn(updated);

            mockMvc.perform(patch("/admin/campaigns/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ENDED"));
        }
    }
}
