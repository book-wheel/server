package com.bookwheel.server.chat.service;

import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatMessageType;
import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.image.ChatImagePolicy;
import com.bookwheel.server.chat.repository.ChatMessageRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatImageMessageTransactionService {

    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public Preparation prepare(String groupId, String userPK, String imageKey) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        validateActiveMember(groupId, userPK);
        ChatRoom chatRoom = findChatRoom(groupId);

        ChatImagePolicy.validateOwnedTemporaryObjectKey(imageKey, chatRoom.getChatRoomId(), userPK);
        String finalImageKey = ChatImagePolicy.toFinalObjectKey(imageKey);
        ChatMessage existingMessage = chatMessageRepository.findByImageKey(finalImageKey).orElse(null);
        return new Preparation(finalImageKey, existingMessage);
    }

    @Transactional
    public ChatMessage persist(String groupId, String userPK, String imageKey) {
        // S3 처리가 끝난 뒤에만 그룹 락을 획득하고, 저장 직전 권한과 채팅방을 다시 검증한다.
        groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        Member member = validateActiveMemberWithUser(groupId, userPK);
        ChatRoom chatRoom = findChatRoom(groupId);

        ChatImagePolicy.validateOwnedTemporaryObjectKey(imageKey, chatRoom.getChatRoomId(), userPK);
        String finalImageKey = ChatImagePolicy.toFinalObjectKey(imageKey);
        ChatMessage existingMessage = chatMessageRepository.findByImageKey(finalImageKey).orElse(null);
        if (existingMessage != null) {
            return existingMessage;
        }

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(member.getUser())
                .messageType(ChatMessageType.IMAGE)
                .imageKey(finalImageKey)
                .build();
        return chatMessageRepository.saveAndFlush(message);
    }

    private ChatRoom findChatRoom(String groupId) {
        return chatRoomRepository.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private Member validateActiveMember(String groupId, String userPK) {
        Member member = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));
        validateActiveStatus(member);
        return member;
    }

    private Member validateActiveMemberWithUser(String groupId, String userPK) {
        Member member = memberRepository.findWithUserByGroupIdAndUserPK(groupId, userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));
        validateActiveStatus(member);
        return member;
    }

    private void validateActiveStatus(Member member) {
        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }
    }

    public record Preparation(String finalImageKey, ChatMessage existingMessage) {
    }
}
