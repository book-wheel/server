package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.GlobalExceptionHandler;
import com.bookwheel.server.common.oauth2.OAuth2LoginCodeService;
import com.bookwheel.server.common.oauth2.OAuth2Pkce;
import com.bookwheel.server.user.dto.OAuth2LoginCodeExchangeRequest;
import com.bookwheel.server.user.dto.OAuth2TokenResponse;
import com.bookwheel.server.user.service.EmailService;
import com.bookwheel.server.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OAuth2LoginCodeService loginCodeService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        loginCodeService = mock(OAuth2LoginCodeService.class);
        AuthController authController = new AuthController(
                mock(UserService.class),
                mock(EmailService.class),
                loginCodeService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("소셜 로그인 시작 시 PKCE challenge를 받고 OAuth2 인증 주소로 이동한다")
    void startsOAuth2LoginWithPkceChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/auth/authorize/google")
                        .param("codeChallenge", CODE_CHALLENGE))
                .andExpect(status().is3xxRedirection())
                .andExpect(request().sessionAttribute(OAuth2Pkce.SESSION_ATTRIBUTE, CODE_CHALLENGE))
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
    }

    @Test
    @DisplayName("PKCE challenge 없이 소셜 로그인을 시작할 수 없다")
    void rejectsOAuth2LoginWithoutPkceChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/auth/authorize/google"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_024"));
    }

    @Test
    @DisplayName("일회용 코드 교환 응답은 캐시하지 않고 토큰을 본문으로 반환한다")
    void exchangesOAuth2LoginCodeWithoutCachingResponse() throws Exception {
        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse(
                "access-token",
                "refresh-token",
                true
        );
        given(loginCodeService.exchange(any(OAuth2LoginCodeExchangeRequest.class)))
                .willReturn(tokenResponse);
        OAuth2LoginCodeExchangeRequest request = new OAuth2LoginCodeExchangeRequest(
                "a".repeat(43),
                "b".repeat(43)
        );

        mockMvc.perform(post("/api/v1/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.isFirstLogin").value(true));
    }
}
