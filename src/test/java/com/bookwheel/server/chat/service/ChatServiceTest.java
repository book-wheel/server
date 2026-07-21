package com.bookwheel.server.chat.service;

import com.bookwheel.server.chat.dto.ChatRoomReadResponse;
import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.entity.ChatRoomReadState;
import com.bookwheel.server.chat.repository.ChatMessageRepository;
import com.bookwheel.server.chat.repository.ChatRoomReadStateRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        User user = User.builder()
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
