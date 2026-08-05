package com.bookwheel.server.chat.controller;

import com.bookwheel.server.chat.dto.ChatImageMessageSendRequest;
import com.bookwheel.server.chat.dto.ChatImagePresignedUrlRequest;
import com.bookwheel.server.chat.dto.ChatImagePresignedUrlResponse;
import com.bookwheel.server.chat.dto.ChatMessageListResponse;
import com.bookwheel.server.chat.dto.ChatMessageResponse;
import com.bookwheel.server.chat.dto.ChatMessageSendRequest;
import com.bookwheel.server.chat.dto.ChatRoomReadRequest;
import com.bookwheel.server.chat.dto.ChatRoomReadResponse;
import com.bookwheel.server.chat.dto.ChatRoomResponse;
import com.bookwheel.server.chat.service.ChatService;
import com.bookwheel.server.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@Tag(name = "Chat", description = "그룹 채팅방 및 메시지 조회 API")
@RequestMapping("/api/v1/groups/{groupId}/chat-room")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "그룹 채팅방 조회", description = "ACTIVE 멤버만 그룹 채팅방 정보와 마지막 읽음 위치를 조회합니다.")
    @GetMapping
    public ApiResponse<ChatRoomResponse> getChatRoom(
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        ChatRoomResponse response = chatService.getChatRoom(groupId, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "채팅 메시지 목록 조회", description = "cursor 이후 메시지를 조회합니다. cursor가 없으면 마지막 읽은 메시지 이후부터 조회합니다.")
    @GetMapping("/messages")
    public ApiResponse<ChatMessageListResponse> getMessages(
            @PathVariable String groupId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal Object principal
    ) {
        ChatMessageListResponse response = chatService.getMessages(groupId, getUserPK(principal), cursor, size);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "채팅 이미지 Presigned URL 발급",
            description = "ACTIVE 멤버에게 최대 5MB 이미지의 S3 임시 업로드용 Presigned PUT URL을 발급합니다. 응답받은 URL로 요청과 동일한 Content-Type 및 파일 크기로 PUT 요청을 보내세요."
    )
    @PostMapping("/images/presigned-url")
    public ApiResponse<ChatImagePresignedUrlResponse> createImagePresignedUrl(
            @PathVariable String groupId,
            @Valid @RequestBody ChatImagePresignedUrlRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        ChatImagePresignedUrlResponse response = chatService.createImagePresignedUrl(
                groupId,
                getUserPK(principal),
                request
        );
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "채팅 텍스트 메시지 전송",
            description = "ACTIVE 멤버가 그룹 채팅방에 최대 1000자의 텍스트 메시지를 전송합니다."
    )
    @PostMapping("/messages")
    public ApiResponse<ChatMessageResponse> sendTextMessage(
            @PathVariable String groupId,
            @Valid @RequestBody ChatMessageSendRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        ChatMessageResponse response = chatService.sendTextMessage(
                groupId,
                getUserPK(principal),
                request.content()
        );
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "채팅 이미지 메시지 전송",
            description = "Presigned URL로 임시 업로드를 완료한 뒤 objectKey를 전달합니다. 서버가 이미지 형식과 크기를 검증해 변경 불가능한 최종 객체로 확정한 후 IMAGE 메시지를 저장합니다."
    )
    @PostMapping("/messages/images")
    public ApiResponse<ChatMessageResponse> sendImageMessage(
            @PathVariable String groupId,
            @Valid @RequestBody ChatImageMessageSendRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        ChatMessageResponse response = chatService.sendImageMessage(
                groupId,
                getUserPK(principal),
                request.imageKey()
        );
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "채팅방 읽음 상태 저장",
            description = "사용자별 채팅방 마지막 읽은 메시지 ID를 저장합니다. 기존 읽음 위치보다 이전이거나 동일한 메시지 ID는 무시합니다."
    )
    @PatchMapping("/read")
    public ApiResponse<ChatRoomReadResponse> updateReadState(
            @PathVariable String groupId,
            @Valid @RequestBody ChatRoomReadRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        ChatRoomReadResponse response = chatService.updateReadState(
                groupId,
                getUserPK(principal),
                request.lastReadMessageId()
        );
        return ApiResponse.success(response);
    }
}
