package com.bookwheel.server.common.controller;

import com.bookwheel.server.common.dto.ImagePresignedUrlResponse;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageControllerTest {

    private static final String USER_PK = "user-pk";
    private static final String PREFIX = "attachments";
    private static final String FILE_NAME = "my_photo.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private S3Service s3Service;

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("공통 Presigned URL 발급 응답은 presignedUrl과 objectKey를 함께 반환한다")
    void getPresignedUrl_Success() throws Exception {
        String objectKey = "attachments/550e8400-e29b-41d4-a716-446655440000_my_photo.png";
        given(s3Service.getPresignedUrl(PREFIX, FILE_NAME)).willReturn(
                new ImagePresignedUrlResponse("https://s3.example.com/upload", objectKey)
        );

        mockMvc.perform(get("/api/v1/images/presigned-url")
                        .param("prefix", PREFIX)
                        .param("fileName", FILE_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.presignedUrl").value("https://s3.example.com/upload"))
                // 클라이언트가 URL을 파싱하지 않도록 objectKey가 응답에 그대로 담겨야 한다.
                .andExpect(jsonPath("$.data.objectKey").value(objectKey));

        then(s3Service).should().getPresignedUrl(PREFIX, FILE_NAME);
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("프로필 예약 prefix로 발급을 요청하면 400을 반환한다")
    void getPresignedUrl_ProfileReservedPrefix_ReturnsBadRequest() throws Exception {
        given(s3Service.getPresignedUrl("profiles-temp/user-pk", FILE_NAME))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/api/v1/images/presigned-url")
                        .param("prefix", "profiles-temp/user-pk")
                        .param("fileName", FILE_NAME))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }
}
