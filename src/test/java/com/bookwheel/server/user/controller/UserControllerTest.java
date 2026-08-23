package com.bookwheel.server.user.controller;

import com.bookwheel.server.user.dto.LoginResponse;
import com.bookwheel.server.user.dto.ProfileImagePresignedUrlRequest;
import com.bookwheel.server.user.dto.ProfileImagePresignedUrlResponse;
import com.bookwheel.server.user.dto.ProfileSetupRequest;
import com.bookwheel.server.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final String USER_PK = "user-pk";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("로그인 사용자가 프로필 이미지 전용 Presigned URL을 발급받는다")
    void createProfileImagePresignedUrl_Success() throws Exception {
        ProfileImagePresignedUrlRequest request = new ProfileImagePresignedUrlRequest(
                "profile.png",
                "image/png",
                123_456L
        );
        ProfileImagePresignedUrlResponse response = new ProfileImagePresignedUrlResponse(
                "https://s3.example.com/upload",
                "profiles-temp/" + USER_PK + "/550e8400-e29b-41d4-a716-446655440000.png",
                "image/png"
        );
        given(userService.createProfileImagePresignedUrl(USER_PK, request)).willReturn(response);

        mockMvc.perform(post("/api/v1/users/profile-image/presigned-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value(response.presignedUrl()))
                .andExpect(jsonPath("$.data.objectKey").value(response.objectKey()))
                .andExpect(jsonPath("$.data.contentType").value("image/png"));

        then(userService).should().createProfileImagePresignedUrl(USER_PK, request);
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("빈 파일명과 0 byte 크기로는 Presigned URL을 발급받을 수 없다")
    void createProfileImagePresignedUrl_InvalidRequest_Rejects() throws Exception {
        mockMvc.perform(post("/api/v1/users/profile-image/presigned-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", " ",
                                "contentType", "image/png",
                                "fileSize", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("setupProfile JSON에 profileImageKey가 없으면 null로 역직렬화해 유지 의미를 전달한다")
    void setupProfile_MissingImageKey_PassesNull() throws Exception {
        LoginResponse response = LoginResponse.builder()
                .userPK(USER_PK)
                .profileImageKey("profiles/current.png")
                .build();
        given(userService.setupProfile(eq(USER_PK), any(ProfileSetupRequest.class))).willReturn(response);

        mockMvc.perform(patch("/api/v1/users/setup-profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "comment", "comment",
                                "nickname", "nickname"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageKey").value("profiles/current.png"));

        then(userService).should().setupProfile(
                USER_PK,
                new ProfileSetupRequest(null, "comment", "nickname")
        );
    }
}
