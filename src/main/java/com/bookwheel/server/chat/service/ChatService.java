package com.bookwheel.server.chat.service;

import com.bookwheel.server.chat.dto.*;
import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatMessageType;
import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.entity.ChatRoomReadState;
import com.bookwheel.server.chat.repository.ChatMessageRepository;
import com.bookwheel.server.chat.repository.ChatRoomReadStateRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int DEFAULT_MESSAGE_SIZE = 30;
    private static final int MAX_MESSAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomReadStateRepository chatRoomReadStateRepository;
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    public ChatRoomResponse getChatRoom(String groupId, String userPK) {
        ChatRoom chatRoom = findAccessibleChatRoom(groupId, userPK);

        Long lastReadMessageId = findLastReadMessageId(chatRoom, userPK);
        long unreadCount = countUnreadMessages(chatRoom, lastReadMessageId);

        return ChatRoomResponse.builder()
                .chatRoomId(chatRoom.getChatRoomId())
                .groupId(chatRoom.getGroup().getGroupId())
                .lastReadMessageId(lastReadMessageId)
                .unreadCount(unreadCount)
                .build();
    }

    public ChatMessageListResponse getMessages(String groupId, String userPK, Long cursor, Integer size) {
        ChatRoom chatRoom = findAccessibleChatRoom(groupId, userPK);
        Long baseCursor = resolveCursor(chatRoom, userPK, cursor);
        PageRequest pageRequest = PageRequest.of(0, normalizeSize(size));

        Slice<ChatMessage> messageSlice = baseCursor == null
                ? chatMessageRepository.findByChatRoomOrderByChatMessageIdAsc(chatRoom, pageRequest)
                : chatMessageRepository.findByChatRoomAndChatMessageIdGreaterThanOrderByChatMessageIdAsc(
                chatRoom,
                baseCursor,
                pageRequest
        );

        List<ChatMessageResponse> messages = messageSlice.getContent()
                .stream()
                .map(this::toMessageResponse)
                .toList();

        Long nextCursor = messages.isEmpty()
                ? baseCursor
                : messages.get(messages.size() - 1).messageId();

        return ChatMessageListResponse.builder()
                .messages(messages)
                .nextCursor(nextCursor)
                .hasNext(messageSlice.hasNext())
                .build();
    }

    @Transactional
    public ChatMessageResponse sendTextMessage(String groupId, String userPK, String content) {
        boolean invalidContent = !StringUtils.hasText(content)
                || content.length() > ChatMessageSendRequest.MAX_CONTENT_LENGTH;
        if (invalidContent) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 그룹 삭제·강퇴·하차와 전송을 직렬화해 검증 이후 채팅방이나 ACTIVE 멤버가 사라지는 것을 막는다.
        findGroupForUpdate(groupId);
        Member member = validateActiveMember(groupId, userPK);
        ChatRoom chatRoom = findChatRoom(groupId);

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(member.getUser())
                .messageType(ChatMessageType.TEXT)
                .content(content)
                .build();

        return toMessageResponse(chatMessageRepository.save(message));
    }

    @Transactional
    public ChatRoomReadResponse updateReadState(String groupId, String userPK, Long lastReadMessageId) {
        // 읽음 상태가 삭제 중인 채팅방에 새로 연결되지 않도록 그룹 행을 먼저 잠근다.
        Group group = findGroupForUpdate(groupId);
        ChatRoom chatRoom = chatRoomRepository.findByGroup_GroupId(group.getGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        Member member = validateActiveMember(groupId, userPK);

        ChatMessage lastReadMessage = chatMessageRepository.findByChatMessageIdAndChatRoom(lastReadMessageId, chatRoom)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        ChatRoomReadState readState = chatRoomReadStateRepository.findByChatRoomAndUser_Id(chatRoom, userPK)
                .orElseGet(() -> ChatRoomReadState.builder()
                        .chatRoom(chatRoom)
                        .user(member.getUser())
                        .build());

        if (readState.advanceLastReadMessage(lastReadMessage)) {
            chatRoomReadStateRepository.save(readState);
        }

        return ChatRoomReadResponse.builder()
                .chatRoomId(chatRoom.getChatRoomId())
                .lastReadMessageId(readState.getLastReadMessage().getChatMessageId())
                .build();
    }

    private ChatRoom findAccessibleChatRoom(String groupId, String userPK) {
        findGroup(groupId);
        validateActiveMember(groupId, userPK);
        return findChatRoom(groupId);
    }

    private Group findGroup(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    // 채팅 데이터를 변경할 때 삭제·멤버 변경과 같은 그룹 행 잠금을 사용한다.
    private Group findGroupForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private ChatRoom findChatRoom(String groupId) {
        return chatRoomRepository.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private Member validateActiveMember(String groupId, String userPK) {
        Member member = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));

        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }

        return member;
    }

    private Long resolveCursor(ChatRoom chatRoom, String userPK, Long cursor) {
        if (cursor != null) {
            if (cursor < 0) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            return cursor == 0 ? null : cursor;
        }

        return findLastReadMessageId(chatRoom, userPK);
    }

    private Long findLastReadMessageId(ChatRoom chatRoom, String userPK) {
        return chatRoomReadStateRepository.findByChatRoomAndUser_Id(chatRoom, userPK)
                .map(ChatRoomReadState::getLastReadMessage)
                .map(ChatMessage::getChatMessageId)
                .orElse(null);
    }

    private long countUnreadMessages(ChatRoom chatRoom, Long lastReadMessageId) {
        if (lastReadMessageId == null) {
            return chatMessageRepository.countByChatRoom(chatRoom);
        }
        return chatMessageRepository.countByChatRoomAndChatMessageIdGreaterThan(chatRoom, lastReadMessageId);
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_MESSAGE_SIZE;
        }
        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return Math.min(size, MAX_MESSAGE_SIZE);
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        User sender = message.getSender();

        return ChatMessageResponse.builder()
                .messageId(message.getChatMessageId())
                .sender(ChatMessageSenderResponse.builder()
                        .userPK(sender.getId())
                        .nickname(sender.getNickname())
                        .profileImageUrl(toImageUrl(sender.getProfileImageKey()))
                        .build())
                .type(message.getMessageType())
                .content(message.getContent())
                .imageKey(message.getImageKey())
                .imageUrl(toMessageImageUrl(message))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String toMessageImageUrl(ChatMessage message) {
        if (message.getMessageType() != ChatMessageType.IMAGE) {
            return null;
        }
        return toImageUrl(message.getImageKey());
    }

    private String toImageUrl(String imageKey) {
        if (!StringUtils.hasText(imageKey)) {
            return null;
        }
        return s3Service.getPresignedGetUrl(imageKey);
    }
}
