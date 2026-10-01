package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.dto.*;
import com.bookwheel.server.admin.entity.*;
import com.bookwheel.server.admin.service.AdminReportService;
import com.bookwheel.server.common.exception.*;
import com.bookwheel.server.common.jwt.*;
import com.bookwheel.server.common.oauth2.CustomOAuth2UserService;
import com.bookwheel.server.common.oauth2.handler.OAuth2SuccessHandler;
import com.bookwheel.server.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminReportService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean AccessTokenRevocationService accessTokenRevocationService;
    @MockitoBean CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test @WithMockUser(username = "admin-pk", roles = "ADMIN")
    void listPassesFiltersAndAuthenticatedAdmin() throws Exception {
        when(service.list("admin-pk", ReportTargetType.COMMENT, ReportStatus.PENDING, 0, 20)).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/admin/reports").param("type", "COMMENT").param("status", "PENDING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        verify(service).list("admin-pk", ReportTargetType.COMMENT, ReportStatus.PENDING, 0, 20);
    }

    @Test @WithMockUser(username = "admin-pk", roles = "ADMIN")
    void detailUsesUnifiedReportId() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/7")).andExpect(status().isOk());
        verify(service).detail("admin-pk", 7L);
    }

    @Test @WithMockUser(username = "admin-pk", roles = "ADMIN")
    void processUsesAuthenticatedAdmin() throws Exception {
        mvc.perform(patch("/api/v1/admin/reports/7/process").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DELETE_AND_BAN\",\"reason\":\"spam\",\"banType\":\"THREE_DAYS\"}"))
            .andExpect(status().isOk());
        verify(service).process("admin-pk", 7L, new ReportProcessRequest("DELETE_AND_BAN", "spam", "THREE_DAYS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"action\":1,\"reason\":\"spam\"}",
        "{\"action\":\"DISMISS\",\"reason\":\" \"}",
        "{\"action\":\"BAN_USER\",\"reason\":\"spam\",\"banType\":\"INVALID\"}"})
    @WithMockUser(roles = "ADMIN")
    void rejectsInvalidProcessingBody(String body) throws Exception {
        mvc.perform(patch("/api/v1/admin/reports/7/process").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rejectsReasonExceedingPenaltyColumnLimit() throws Exception {
        mvc.perform(patch("/api/v1/admin/reports/7/process").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"BAN_USER\",\"reason\":\"" + "가".repeat(256) + "\",\"banType\":\"PERMANENT\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "admin-pk", roles = "ADMIN")
    void acceptsReasonAtPenaltyColumnLimit() throws Exception {
        String reason = "가".repeat(255);
        mvc.perform(patch("/api/v1/admin/reports/7/process").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"BAN_USER\",\"reason\":\"" + reason + "\",\"banType\":\"PERMANENT\"}"))
            .andExpect(status().isOk());
        verify(service).process("admin-pk", 7L, new ReportProcessRequest("BAN_USER", reason, "PERMANENT"));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rejectsInvalidFilter() throws Exception {
        mvc.perform(get("/api/v1/admin/reports").param("type", "INVALID"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "admin-pk", roles = "ADMIN")
    void returnsDuplicateProcessingError() throws Exception {
        when(service.process(eq("admin-pk"), eq(7L), any()))
            .thenThrow(new BusinessException(ErrorCode.ALREADY_PROCESSED_REPORT));
        mvc.perform(patch("/api/v1/admin/reports/7/process").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISMISS\",\"reason\":\"reviewed\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REPORT_002"));
    }

    @Test void anonymousCannotReadOrProcess() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reports/7")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/reports/7/process")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "USER")
    void userCannotReadOrProcess() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports/7")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/reports/7/process")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "ONBOARDING")
    void onboardingCannotReadReports() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
