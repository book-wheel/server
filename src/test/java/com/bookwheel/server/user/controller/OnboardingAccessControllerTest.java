package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import com.bookwheel.server.common.jwt.JwtAuthenticationEntryPoint;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.oauth2.CustomOAuth2UserService;
import com.bookwheel.server.common.oauth2.handler.OAuth2SuccessHandler;
import com.bookwheel.server.config.SecurityConfig;
import com.bookwheel.server.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class OnboardingAccessControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private AccessTokenRevocationService accessTokenRevocationService;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    @WithMockUser(username = "user-pk", roles = "ONBOARDING")
    @DisplayName("온보딩 토큰은 프로필 설정 API를 호출할 수 있다")
    void onboardingCanSetupProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/users/setup-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user-pk", roles = "ONBOARDING")
    @DisplayName("온보딩 토큰은 일반 서비스 API를 호출할 수 없다")
    void onboardingCannotUseRegularApi() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }
}
