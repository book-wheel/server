package com.bookwheel.server.chat.service;

import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatMessageType;
import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.repository.ChatMessageRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatImageMessageTransactionServiceTest {

    private static final String GROUP_ID = "group-1";

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatImageMessageTransactionService transactionService;
    private Group group;
    private User user;
    private Member member;
    private ChatRoom chatRoom;
    private String userPK;
    private String temporaryImageKey;
    private String finalImageKey;

    @BeforeEach
    void setUp() {
        transactionService = new ChatImageMessageTransactionService(
                groupRepository,
                memberRepository,
                chatRoomRepository,
                chatMessageRepository
        );
        group = Group.builder()
                .groupId(GROUP_ID)
                .groupName("채팅 모임")
                .build();
        user = User.builder()
                .loginId("chat-user")
                .password("password")
                .nickname("채팅 사용자")
                .mail("chat@example.com")
                .isActive(true)
                .build();
        userPK = user.getId();
        temporaryImageKey = "chat-temp/chat-room-1/" + userPK
                + "/550e8400-e29b-41d4-a716-446655440000.png";
        finalImageKey = "chat/chat-room-1/" + userPK
                + "/550e8400-e29b-41d4-a716-446655440000.png";
        member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
        chatRoom = ChatRoom.builder()
                .chatRoomId("chat-room-1")
                .group(group)
                .build();
    }

    @Test
    @DisplayName("S3 처리 전에는 그룹 쓰기 락 없이 접근 권한과 기존 메시지만 확인한다")
    void prepare_UsesNonLockingReadAndFindsExistingMessage() {
        ChatMessage existingMessage = imageMessage(10L);
        givenReadAccess();
        given(chatMessageRepository.findByImageKey(finalImageKey))
                .willReturn(Optional.of(existingMessage));

        ChatImageMessageTransactionService.Preparation preparation = transactionService.prepare(
                GROUP_ID,
                userPK,
                temporaryImageKey
        );

        assertThat(preparation.finalImageKey()).isEqualTo(finalImageKey);
        assertThat(preparation.existingMessage()).isSameAs(existingMessage);
        then(groupRepository).should(never()).findByGroupIdForUpdate(GROUP_ID);
    }

    @Test
    @DisplayName("S3 처리 후 그룹을 잠그고 ACTIVE 멤버를 재검증한 뒤 이미지 메시지를 저장한다")
    void persist_RevalidatesUnderGroupLockAndSavesMessage() {
        ChatMessage savedMessage = imageMessage(10L);
        givenWriteAccess();
        given(chatMessageRepository.findByImageKey(finalImageKey)).willReturn(Optional.empty());
        given(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).willReturn(savedMessage);

        ChatMessage result = transactionService.persist(GROUP_ID, userPK, temporaryImageKey);

        assertThat(result).isSameAs(savedMessage);
        then(groupRepository).should().findByGroupIdForUpdate(GROUP_ID);
        then(memberRepository).should().findWithUserByGroupIdAndUserPK(GROUP_ID, userPK);
        then(chatMessageRepository).should().saveAndFlush(org.mockito.ArgumentMatchers.argThat(message ->
                message.getChatRoom() == chatRoom
                        && message.getSender() == user
                        && message.getMessageType() == ChatMessageType.IMAGE
                        && finalImageKey.equals(message.getImageKey())
        ));
    }

    @Test
    @DisplayName("S3 처리 중 멤버가 비활성화되면 저장 직전 재검증에서 거부한다")
    void persist_RejectsMemberWhoBecameInactive() {
        Member inactiveMember = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(user)
                .memberRole(MemberRole.OUT)
                .memberStatus(MemberStatus.EXITED)
                .build();
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findWithUserByGroupIdAndUserPK(GROUP_ID, userPK))
                .willReturn(Optional.of(inactiveMember));

        assertThatThrownBy(() -> transactionService.persist(GROUP_ID, userPK, temporaryImageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);

        then(chatRoomRepository).shouldHaveNoInteractions();
        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("동시 재시도로 기존 메시지가 저장됐으면 새 메시지를 만들지 않고 반환한다")
    void persist_ReturnsExistingMessageAfterLock() {
        ChatMessage existingMessage = imageMessage(10L);
        givenWriteAccess();
        given(chatMessageRepository.findByImageKey(finalImageKey))
                .willReturn(Optional.of(existingMessage));

        ChatMessage result = transactionService.persist(GROUP_ID, userPK, temporaryImageKey);

        assertThat(result).isSameAs(existingMessage);
        then(chatMessageRepository).should(never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    @DisplayName("S3 오케스트레이터는 트랜잭션을 중단하고 준비와 저장 단계만 각각 트랜잭션을 연다")
    void transactionBoundaries_AreDeclaredExplicitly() throws NoSuchMethodException {
        Method sendImageMessage = ChatService.class.getMethod(
                "sendImageMessage",
                String.class,
                String.class,
                String.class
        );
        Method prepare = ChatImageMessageTransactionService.class.getMethod(
                "prepare",
                String.class,
                String.class,
                String.class
        );
        Method persist = ChatImageMessageTransactionService.class.getMethod(
                "persist",
                String.class,
                String.class,
                String.class
        );

        assertThat(sendImageMessage.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(prepare.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(persist.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    private void givenReadAccess() {
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK))
                .willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.of(chatRoom));
    }

    private void givenWriteAccess() {
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findWithUserByGroupIdAndUserPK(GROUP_ID, userPK))
                .willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.of(chatRoom));
    }

    private ChatMessage imageMessage(Long messageId) {
        return ChatMessage.builder()
                .chatMessageId(messageId)
                .chatRoom(chatRoom)
                .sender(user)
                .messageType(ChatMessageType.IMAGE)
                .imageKey(finalImageKey)
                .createdAt(LocalDateTime.of(2026, 8, 4, 12, 0))
                .build();
    }
}
