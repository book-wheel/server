package com.bookwheel.server.chat.controller;

import com.bookwheel.server.chat.dto.ChatMessageResponse;
import com.bookwheel.server.chat.dto.ChatMessageSenderResponse;
import com.bookwheel.server.chat.dto.ChatImagePresignedUrlRequest;
import com.bookwheel.server.chat.dto.ChatImagePresignedUrlResponse;
import com.bookwheel.server.chat.entity.ChatMessageType;
import com.bookwheel.server.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static com.bookwheel.server.chat.dto.ChatMessageSendRequest.MAX_CONTENT_LENGTH;
import static com.bookwheel.server.chat.image.ChatImagePolicy.MAX_FILE_SIZE;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    private static final String GROUP_ID = "group-1";
    private static final String USER_PK = "user-pk";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatService chatService;

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("로그인 사용자가 텍스트 메시지를 전송하면 생성된 메시지를 반환한다")
    void sendTextMessage_Success() throws Exception {
        String content = "안녕하세요!";
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 24, 12, 30, 15);
        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(1L)
                .sender(ChatMessageSenderResponse.builder()
                        .userPK(USER_PK)
                        .nickname("채팅 사용자")
                        .profileImageUrl(null)
                        .build())
                .type(ChatMessageType.TEXT)
                .content(content)
                .createdAt(createdAt)
                .build();
        given(chatService.sendTextMessage(GROUP_ID, USER_PK, content)).willReturn(response);

        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/messages", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageId").value(1L))
                .andExpect(jsonPath("$.data.sender.userPK").value(USER_PK))
                .andExpect(jsonPath("$.data.sender.nickname").value("채팅 사용자"))
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.content").value(content))
                .andExpect(jsonPath("$.data.createdAt").value(createdAt.toString()));

        then(chatService).should().sendTextMessage(GROUP_ID, USER_PK, content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    @WithMockUser(username = USER_PK)
    @DisplayName("빈 문자열 또는 공백 메시지 전송 요청은 거부한다")
    void sendTextMessage_RejectsBlankContent(String content) throws Exception {
        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/messages", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        then(chatService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("최대 길이를 초과한 메시지 전송 요청은 거부한다")
    void sendTextMessage_RejectsContentOverMaxLength() throws Exception {
        String content = "a".repeat(MAX_CONTENT_LENGTH + 1);

        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/messages", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        then(chatService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("로그인 사용자가 채팅 이미지 Presigned URL을 발급받는다")
    void createImagePresignedUrl_Success() throws Exception {
        ChatImagePresignedUrlRequest request = new ChatImagePresignedUrlRequest(
                "cat.png",
                "image/png",
                123_456L
        );
        ChatImagePresignedUrlResponse response = ChatImagePresignedUrlResponse.builder()
                .presignedUrl("https://s3.example.com/upload")
                .objectKey("chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png")
                .build();
        given(chatService.createImagePresignedUrl(GROUP_ID, USER_PK, request)).willReturn(response);

        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/images/presigned-url", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.presignedUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.data.objectKey")
                        .value("chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png"));

        then(chatService).should().createImagePresignedUrl(GROUP_ID, USER_PK, request);
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("5MB를 초과한 이미지 Presigned URL 요청은 거부한다")
    void createImagePresignedUrl_RejectsOversizedFile() throws Exception {
        Map<String, Object> request = Map.of(
                "fileName", "cat.png",
                "contentType", "image/png",
                "fileSize", MAX_FILE_SIZE + 1
        );

        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/images/presigned-url", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        then(chatService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("업로드된 이미지 키로 IMAGE 메시지를 저장한다")
    void sendImageMessage_Success() throws Exception {
        String temporaryImageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        String finalImageKey = "chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        String imageUrl = "https://s3.example.com/image";
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 12, 30, 15);
        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(10L)
                .sender(ChatMessageSenderResponse.builder()
                        .userPK(USER_PK)
                        .nickname("채팅 사용자")
                        .build())
                .type(ChatMessageType.IMAGE)
                .imageKey(finalImageKey)
                .imageUrl(imageUrl)
                .createdAt(createdAt)
                .build();
        given(chatService.sendImageMessage(GROUP_ID, USER_PK, temporaryImageKey)).willReturn(response);

        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/messages/images", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageKey", temporaryImageKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageId").value(10L))
                .andExpect(jsonPath("$.data.sender.userPK").value(USER_PK))
                .andExpect(jsonPath("$.data.type").value("IMAGE"))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.imageKey").value(finalImageKey))
                .andExpect(jsonPath("$.data.imageUrl").value(imageUrl))
                .andExpect(jsonPath("$.data.createdAt").value(createdAt.toString()));

        then(chatService).should().sendImageMessage(GROUP_ID, USER_PK, temporaryImageKey);
    }

    @Test
    @WithMockUser(username = USER_PK)
    @DisplayName("빈 이미지 키 메시지 요청은 거부한다")
    void sendImageMessage_RejectsBlankImageKey() throws Exception {
        mockMvc.perform(post("/api/v1/groups/{groupId}/chat-room/messages/images", GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageKey", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        then(chatService).shouldHaveNoInteractions();
    }
}
