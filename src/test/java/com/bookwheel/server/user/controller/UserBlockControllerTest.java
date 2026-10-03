package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import com.bookwheel.server.common.jwt.JwtAuthenticationEntryPoint;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.UserAuthenticationStatusService;
import com.bookwheel.server.common.oauth2.CustomOAuth2UserService;
import com.bookwheel.server.common.oauth2.handler.OAuth2SuccessHandler;
import com.bookwheel.server.config.SecurityConfig;
import com.bookwheel.server.user.dto.UserBlockResponse;
import com.bookwheel.server.user.service.UserBlockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserBlockController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class UserBlockControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserBlockService service;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean AccessTokenRevocationService accessTokenRevocationService;
    @MockitoBean UserAuthenticationStatusService userAuthenticationStatusService;
    @MockitoBean CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test @WithMockUser(username = "owner")
    void blockUsesAuthenticatedRequester() throws Exception {
        mvc.perform(put("/api/v1/users/me/blocks/target"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).block("owner", "target");
    }

    @Test @WithMockUser(username = "owner")
    void unblockUsesAuthenticatedRequester() throws Exception {
        mvc.perform(delete("/api/v1/users/me/blocks/target"))
            .andExpect(status().isOk());
        verify(service).unblock("owner", "target");
    }

    @Test @WithMockUser(username = "owner")
    void listReturnsContractAndDefaultPagination() throws Exception {
        when(service.getBlocks("owner", 0, 20)).thenReturn(new PageImpl<>(List.of(
            new UserBlockResponse("target", "nickname", null, LocalDateTime.of(2026, 10, 1, 12, 0)))));
        mvc.perform(get("/api/v1/users/me/blocks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].userPK").value("target"))
            .andExpect(jsonPath("$.data.content[0].nickname").value("nickname"))
            .andExpect(jsonPath("$.data.content[0].blockedAt").value("2026-10-01T12:00:00"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
        verify(service).getBlocks("owner", 0, 20);
    }

    @Test @WithMockUser(username = "owner")
    void selfBlockReturnsDocumentedError() throws Exception {
        doThrow(new BusinessException(ErrorCode.CANNOT_BLOCK_SELF)).when(service).block("owner", "owner");
        mvc.perform(put("/api/v1/users/me/blocks/owner"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BLOCK_001"));
    }

    @Test @WithMockUser(username = "owner")
    void invalidPageReturnsBadRequest() throws Exception {
        when(service.getBlocks("owner", -1, 20)).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        mvc.perform(get("/api/v1/users/me/blocks").param("page", "-1"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));
        mvc.perform(get("/api/v1/users/me/blocks").param("size", "invalid"))
            .andExpect(status().isBadRequest());
    }

    @Test void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me/blocks")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/blocks/target")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/me/blocks/target")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "onboarding", roles = "ONBOARDING")
    void onboardingRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me/blocks")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/me/blocks/target")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/me/blocks/target")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
