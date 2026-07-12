package com.bookwheel.server.chat.controller;

import com.bookwheel.server.chat.dto.ChatMessageListResponse;
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

    @Operation(summary = "채팅방 읽음 상태 저장", description = "사용자별 채팅방 마지막 읽은 메시지 ID를 저장합니다.")
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
