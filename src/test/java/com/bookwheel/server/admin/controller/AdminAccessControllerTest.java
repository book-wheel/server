package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.service.AdminService;
import com.bookwheel.server.common.jwt.JwtAuthenticationEntryPoint;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.oauth2.CustomOAuth2UserService;
import com.bookwheel.server.common.oauth2.handler.OAuth2SuccessHandler;
import com.bookwheel.server.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("관리자 API는 일반 유저 접근 차단")
    void adminApi_Forbidden_ForUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isForbidden());
    }
}
