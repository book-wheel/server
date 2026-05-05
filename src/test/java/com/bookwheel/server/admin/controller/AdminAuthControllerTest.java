package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.dto.AdminLoginRequest;
import com.bookwheel.server.admin.dto.AdminLoginResponse;
import com.bookwheel.server.admin.service.AdminAuthService;
import com.bookwheel.server.common.jwt.JwtAuthenticationEntryPoint;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.oauth2.CustomOAuth2UserService;
import com.bookwheel.server.common.oauth2.handler.OAuth2SuccessHandler;
import com.bookwheel.server.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuthController.class)
@Import(SecurityConfig.class)
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    @DisplayName("관리자 로그인 API는 인증 없이 호출 가능")
    void login_PermitAll() throws Exception {
        AdminLoginResponse response = AdminLoginResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .adminPK("admin-pk")
                .loginId("admin")
                .name("관리자")
                .build();

        given(adminAuthService.login(any(AdminLoginRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest("admin", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminPK").value("admin-pk"))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }
}
