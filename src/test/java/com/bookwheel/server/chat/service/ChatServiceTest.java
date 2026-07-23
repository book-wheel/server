package com.bookwheel.server.chat.service;

import com.bookwheel.server.chat.dto.ChatMessageResponse;
import com.bookwheel.server.chat.dto.ChatRoomReadResponse;
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
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String GROUP_ID = "group-1";

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomReadStateRepository chatRoomReadStateRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Service s3Service;

    private ChatService chatService;
    private Group group;
    private ChatRoom chatRoom;
    private Member member;
    private User user;
    private String userPK;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                chatRoomReadStateRepository,
                groupRepository,
                memberRepository,
                s3Service
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
        chatRoom = ChatRoom.builder()
                .chatRoomId("chat-room-1")
                .group(group)
                .build();
        member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("ACTIVE 멤버가 전송한 텍스트 메시지를 로그인 사용자 작성자로 저장한다")
    void sendTextMessage_SavesMessageWithAuthenticatedUser() {
        String content = "안녕하세요!";
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 24, 12, 30);
        ChatMessage savedMessage = ChatMessage.builder()
                .chatMessageId(1L)
                .chatRoom(chatRoom)
                .sender(user)
                .messageType(ChatMessageType.TEXT)
                .content(content)
                .createdAt(createdAt)
                .build();
        givenSendAccess();
        given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

        ChatMessageResponse response = chatService.sendTextMessage(GROUP_ID, userPK, content);

        assertThat(response.messageId()).isEqualTo(1L);
        assertThat(response.sender().userPK()).isEqualTo(userPK);
        assertThat(response.sender().nickname()).isEqualTo(user.getNickname());
        assertThat(response.type()).isEqualTo(ChatMessageType.TEXT);
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        then(chatMessageRepository).should().save(org.mockito.ArgumentMatchers.argThat(message ->
                message.getChatRoom() == chatRoom
                        && message.getSender() == user
                        && message.getMessageType() == ChatMessageType.TEXT
                        && content.equals(message.getContent())
                        && message.getImageKey() == null
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("빈 문자열 또는 공백 메시지는 저장하지 않는다")
    void sendTextMessage_RejectsBlankContent(String content) {
        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, content))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 그룹에는 메시지를 전송할 수 없다")
    void sendTextMessage_RejectsMissingGroup() {
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, "메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("그룹에 속하지 않은 사용자는 메시지를 전송할 수 없다")
    void sendTextMessage_RejectsNonMember() {
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, "메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비ACTIVE 멤버는 메시지를 전송할 수 없다")
    void sendTextMessage_RejectsInactiveMember() {
        Member inactiveMember = Member.builder()
                .memberId("inactive-member")
                .group(group)
                .user(user)
                .memberRole(MemberRole.OUT)
                .memberStatus(MemberStatus.EXITED)
                .build();
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK))
                .willReturn(Optional.of(inactiveMember));

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, "메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("채팅방이 존재하지 않으면 메시지를 전송할 수 없다")
    void sendTextMessage_RejectsMissingChatRoom() {
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, "메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("읽음 상태가 없으면 요청한 메시지로 새 상태를 생성한다")
    void updateReadState_CreatesInitialState() {
        ChatMessage requestedMessage = message(80L);
        givenCommonState(requestedMessage);
        given(chatRoomReadStateRepository.findByChatRoomAndUser_Id(chatRoom, userPK))
                .willReturn(Optional.empty());

        ChatRoomReadResponse response = chatService.updateReadState(GROUP_ID, userPK, 80L);

        assertThat(response.lastReadMessageId()).isEqualTo(80L);
        then(chatRoomReadStateRepository).should().save(org.mockito.ArgumentMatchers.argThat(readState ->
                readState.getChatRoom() == chatRoom
                        && readState.getUser() == member.getUser()
                        && readState.getLastReadMessage() == requestedMessage
        ));
    }

    @Test
    @DisplayName("요청한 메시지가 기존 읽음 위치보다 앞서면 갱신한다")
    void updateReadState_AdvancesToNewerMessage() {
        ChatMessage currentMessage = message(80L);
        ChatMessage requestedMessage = message(100L);
        ChatRoomReadState readState = readState(currentMessage);
        givenCommonState(requestedMessage);
        given(chatRoomReadStateRepository.findByChatRoomAndUser_Id(chatRoom, userPK))
                .willReturn(Optional.of(readState));

        ChatRoomReadResponse response = chatService.updateReadState(GROUP_ID, userPK, 100L);

        assertThat(readState.getLastReadMessage()).isSameAs(requestedMessage);
        assertThat(response.lastReadMessageId()).isEqualTo(100L);
        then(chatRoomReadStateRepository).should().save(readState);
    }

    @Test
    @DisplayName("늦게 도착한 이전 메시지 요청은 기존 읽음 위치를 유지한다")
    void updateReadState_IgnoresOlderMessage() {
        ChatMessage currentMessage = message(100L);
        ChatMessage requestedMessage = message(80L);
        ChatRoomReadState readState = readState(currentMessage);
        givenCommonState(requestedMessage);
        given(chatRoomReadStateRepository.findByChatRoomAndUser_Id(chatRoom, userPK))
                .willReturn(Optional.of(readState));

        ChatRoomReadResponse response = chatService.updateReadState(GROUP_ID, userPK, 80L);

        assertThat(readState.getLastReadMessage()).isSameAs(currentMessage);
        assertThat(response.lastReadMessageId()).isEqualTo(100L);
        then(chatRoomReadStateRepository).should(never()).save(readState);
    }

    @Test
    @DisplayName("동일한 메시지 읽음 요청은 기존 상태를 유지한다")
    void updateReadState_IgnoresSameMessage() {
        ChatMessage currentMessage = message(100L);
        ChatRoomReadState readState = readState(currentMessage);
        givenCommonState(currentMessage);
        given(chatRoomReadStateRepository.findByChatRoomAndUser_Id(chatRoom, userPK))
                .willReturn(Optional.of(readState));

        ChatRoomReadResponse response = chatService.updateReadState(GROUP_ID, userPK, 100L);

        assertThat(readState.getLastReadMessage()).isSameAs(currentMessage);
        assertThat(response.lastReadMessageId()).isEqualTo(100L);
        then(chatRoomReadStateRepository).should(never()).save(readState);
    }

    private void givenCommonState(ChatMessage requestedMessage) {
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.of(member));
        given(chatMessageRepository.findByChatMessageIdAndChatRoom(
                requestedMessage.getChatMessageId(),
                chatRoom
        )).willReturn(Optional.of(requestedMessage));
    }

    private void givenSendAccess() {
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.of(chatRoom));
    }

    private ChatMessage message(Long messageId) {
        return ChatMessage.builder()
                .chatMessageId(messageId)
                .chatRoom(chatRoom)
                .sender(member.getUser())
                .build();
    }

    private ChatRoomReadState readState(ChatMessage lastReadMessage) {
        return ChatRoomReadState.builder()
                .chatRoom(chatRoom)
                .user(member.getUser())
                .lastReadMessage(lastReadMessage)
                .build();
    }
}
