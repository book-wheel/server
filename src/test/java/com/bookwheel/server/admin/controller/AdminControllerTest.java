package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.dto.AdminBanRequest;
import com.bookwheel.server.admin.dto.AdminBanResponse;
import com.bookwheel.server.admin.dto.BanReason;
import com.bookwheel.server.admin.repository.PenaltyRepository;
import com.bookwheel.server.admin.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("SUCCESS: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println("FAIL: " + context.getDisplayName());
            System.out.println("이유: " + cause.getMessage());
        }
    };

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("유저 밴 API 호출 성공")
    void banUser_Success() throws Exception {
        // given
        String userPk = "user123";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.ETC, "스팸/도배");        AdminBanResponse response = AdminBanResponse.builder()
                .userId(userPk)
                .nickname("테스트유저")
                .banType("SUSPEND")
                .reasonMessage("스팸/도배")
                .build();

        given(adminService.banUser(eq(userPk), any(AdminBanRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/admin/users/{userPk}/ban", userPk)
                        .with(csrf()) // Security 처리
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userPk))
                .andExpect(jsonPath("$.data.banType").value("SUSPEND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("신고 처리 API 호출 성공")
    void processReport_Success() throws Exception {
        Long reportId = 1L;

        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}/process", reportId)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("1신고처리 api 연결 성공"));
    }
}