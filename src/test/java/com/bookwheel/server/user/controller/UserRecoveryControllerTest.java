package com.bookwheel.server.user.controller;

import com.bookwheel.server.user.dto.IdRecoveryResponse;
import com.bookwheel.server.user.dto.PasswordResetRequest;
import com.bookwheel.server.user.dto.RecoveryCodeRequest;
import com.bookwheel.server.user.dto.RecoveryEmailRequest;
import com.bookwheel.server.user.service.UserRecoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserRecoveryController.class)
class UserRecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRecoveryService userRecoveryService;

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
    @WithMockUser
    @DisplayName("User-Recovery: send recovery code success")
    void sendCode_Success() throws Exception {
        RecoveryEmailRequest request = new RecoveryEmailRequest("test@example.com");

        mockMvc.perform(post("/api/v1/users/recovery/send-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("User-Recovery: send recovery code validation error")
    void sendCode_ValidationError() throws Exception {
        RecoveryEmailRequest request = new RecoveryEmailRequest("not-an-email");

        mockMvc.perform(post("/api/v1/users/recovery/send-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("User-Recovery: verify id success")
    void verifyId_Success() throws Exception {
        RecoveryCodeRequest request = new RecoveryCodeRequest("test@example.com", "123456");
        given(userRecoveryService.verifyCodeAndFindId(any(RecoveryCodeRequest.class)))
                .willReturn(new IdRecoveryResponse("masked-id"));

        mockMvc.perform(post("/api/v1/users/recovery/verify-id")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("masked-id"));
    }

    @Test
    @WithMockUser
    @DisplayName("User-Recovery: verify password success")
    void verifyPassword_Success() throws Exception {
        RecoveryCodeRequest request = new RecoveryCodeRequest("test@example.com", "123456");
        given(userRecoveryService.verifyCodeForPassword(any(RecoveryCodeRequest.class)))
                .willReturn("reset-token-123");

        mockMvc.perform(post("/api/v1/users/recovery/verify-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("reset-token-123"));
    }

    @Test
    @WithMockUser
    @DisplayName("User-Recovery: reset password success")
    void resetPassword_Success() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("token-abc", "Abcd1234!");

        mockMvc.perform(patch("/api/v1/users/recovery/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("User-Recovery: reset password validation error")
    void resetPassword_ValidationError() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("token-abc", "short");

        mockMvc.perform(patch("/api/v1/users/recovery/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
