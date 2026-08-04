package com.bookwheel.server.chat.service;

import com.bookwheel.server.chat.dto.ChatMessageResponse;
import com.bookwheel.server.chat.dto.ChatImagePresignedUrlRequest;
import com.bookwheel.server.chat.dto.ChatImagePresignedUrlResponse;
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
import com.bookwheel.server.common.dto.S3ObjectMetadata;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.bookwheel.server.chat.dto.ChatMessageSendRequest.MAX_CONTENT_LENGTH;
import static com.bookwheel.server.chat.image.ChatImagePolicy.MAX_FILE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String GROUP_ID = "group-1";
    private static final String ETAG = "\"image-etag\"";
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

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

    @Mock
    private ChatImageMessageTransactionService chatImageMessageTransactionService;

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
                s3Service,
                chatImageMessageTransactionService
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
    @DisplayName("최대 길이를 초과한 메시지는 저장하지 않는다")
    void sendTextMessage_RejectsContentOverMaxLength() {
        String content = "a".repeat(MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, content))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        then(groupRepository).shouldHaveNoInteractions();
        then(memberRepository).shouldHaveNoInteractions();
        then(chatRoomRepository).shouldHaveNoInteractions();
        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 그룹에는 메시지를 전송할 수 없다")
    void sendTextMessage_RejectsMissingGroup() {
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, "메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("그룹에 속하지 않은 사용자는 메시지를 전송할 수 없다")
    void sendTextMessage_RejectsNonMember() {
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
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
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
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
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendTextMessage(GROUP_ID, userPK, "메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);

        then(chatMessageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ACTIVE 멤버에게 채팅방 전용 이미지 Presigned URL을 발급한다")
    void createImagePresignedUrl_ReturnsChatObjectKey() {
        ChatImagePresignedUrlRequest request = new ChatImagePresignedUrlRequest(
                "cat.png",
                "image/png",
                123_456L
        );
        givenPresignedUrlAccess();
        given(s3Service.getPresignedPutUrl(anyString(), eq("image/png"), eq(123_456L)))
                .willReturn("https://s3.example.com/upload");

        ChatImagePresignedUrlResponse response = chatService.createImagePresignedUrl(
                GROUP_ID,
                userPK,
                request
        );

        assertThat(response.presignedUrl()).isEqualTo("https://s3.example.com/upload");
        assertThat(response.objectKey())
                .startsWith("chat-temp/" + chatRoom.getChatRoomId() + "/" + userPK + "/")
                .endsWith(".png");
        then(s3Service).should().getPresignedPutUrl(response.objectKey(), "image/png", 123_456L);
    }

    @Test
    @DisplayName("5MB를 초과한 이미지에는 Presigned URL을 발급하지 않는다")
    void createImagePresignedUrl_RejectsOversizedFile() {
        ChatImagePresignedUrlRequest request = new ChatImagePresignedUrlRequest(
                "cat.png",
                "image/png",
                MAX_FILE_SIZE + 1
        );

        assertThatThrownBy(() -> chatService.createImagePresignedUrl(GROUP_ID, userPK, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

        then(groupRepository).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("확장자와 MIME 타입이 일치하지 않으면 Presigned URL을 발급하지 않는다")
    void createImagePresignedUrl_RejectsMismatchedContentType() {
        ChatImagePresignedUrlRequest request = new ChatImagePresignedUrlRequest(
                "cat.png",
                "image/jpeg",
                100L
        );

        assertThatThrownBy(() -> chatService.createImagePresignedUrl(GROUP_ID, userPK, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        then(groupRepository).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비ACTIVE 멤버에게는 이미지 Presigned URL을 발급하지 않는다")
    void createImagePresignedUrl_RejectsInactiveMember() {
        ChatImagePresignedUrlRequest request = new ChatImagePresignedUrlRequest(
                "cat.png",
                "image/png",
                100L
        );
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

        assertThatThrownBy(() -> chatService.createImagePresignedUrl(GROUP_ID, userPK, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);

        then(chatRoomRepository).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("업로드된 이미지를 로그인 사용자 작성자의 IMAGE 메시지로 저장한다")
    void sendImageMessage_SavesUploadedImageWithAuthenticatedUser() {
        String temporaryImageKey = temporaryImageKey("png");
        String finalImageKey = finalImageKey("png");
        String imageUrl = "https://s3.example.com/image";
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 12, 30);
        ChatMessage savedMessage = ChatMessage.builder()
                .chatMessageId(10L)
                .chatRoom(chatRoom)
                .sender(user)
                .messageType(ChatMessageType.IMAGE)
                .imageKey(finalImageKey)
                .createdAt(createdAt)
                .build();
        givenImagePreparation(temporaryImageKey, finalImageKey);
        given(s3Service.getObjectMetadata(temporaryImageKey))
                .willReturn(new S3ObjectMetadata(123_456L, "image/png", ETAG));
        given(s3Service.getObjectSignature(temporaryImageKey, ETAG, 12)).willReturn(PNG_SIGNATURE);
        given(chatImageMessageTransactionService.persist(GROUP_ID, userPK, temporaryImageKey))
                .willReturn(savedMessage);
        given(s3Service.getPresignedGetUrl(finalImageKey)).willReturn(imageUrl);

        ChatMessageResponse response = chatService.sendImageMessage(GROUP_ID, userPK, temporaryImageKey);

        assertThat(response.messageId()).isEqualTo(10L);
        assertThat(response.sender().userPK()).isEqualTo(userPK);
        assertThat(response.type()).isEqualTo(ChatMessageType.IMAGE);
        assertThat(response.content()).isNull();
        assertThat(response.imageKey()).isEqualTo(finalImageKey);
        assertThat(response.imageUrl()).isEqualTo(imageUrl);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        InOrder order = inOrder(chatImageMessageTransactionService, s3Service);
        order.verify(chatImageMessageTransactionService).prepare(GROUP_ID, userPK, temporaryImageKey);
        order.verify(s3Service).getObjectMetadata(temporaryImageKey);
        order.verify(s3Service).getObjectSignature(temporaryImageKey, ETAG, 12);
        order.verify(s3Service).copyObjectIfUnchanged(temporaryImageKey, finalImageKey, ETAG);
        order.verify(chatImageMessageTransactionService).persist(GROUP_ID, userPK, temporaryImageKey);
        then(s3Service).should().deleteObject(temporaryImageKey);
        then(s3Service).should(never()).deleteObject(finalImageKey);
    }

    @Test
    @DisplayName("다른 사용자의 이미지 키로 메시지를 저장할 수 없다")
    void sendImageMessage_RejectsAnotherUsersObjectKey() {
        String imageKey = "chat-temp/" + chatRoom.getChatRoomId()
                + "/another-user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        given(chatImageMessageTransactionService.prepare(GROUP_ID, userPK, imageKey))
                .willThrow(new BusinessException(ErrorCode.INVALID_FILE_KEY));

        assertThatThrownBy(() -> chatService.sendImageMessage(GROUP_ID, userPK, imageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_KEY);

        then(s3Service).shouldHaveNoInteractions();
        then(chatImageMessageTransactionService).should(never()).persist(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("S3에 업로드되지 않은 이미지 키로 메시지를 저장할 수 없다")
    void sendImageMessage_RejectsMissingObject() {
        String imageKey = temporaryImageKey("png");
        givenImagePreparation(imageKey, finalImageKey("png"));
        given(s3Service.getObjectMetadata(imageKey))
                .willThrow(new BusinessException(ErrorCode.FILE_NOT_FOUND));

        assertThatThrownBy(() -> chatService.sendImageMessage(GROUP_ID, userPK, imageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);

        then(chatImageMessageTransactionService).should(never()).persist(anyString(), anyString(), anyString());
        then(s3Service).should().deleteObject(imageKey);
        then(s3Service).should(never()).deleteObject(finalImageKey("png"));
    }

    @Test
    @DisplayName("실제 업로드된 이미지의 MIME 타입이 키 확장자와 다르면 메시지를 저장하지 않는다")
    void sendImageMessage_RejectsUploadedContentTypeMismatch() {
        String imageKey = temporaryImageKey("png");
        givenImagePreparation(imageKey, finalImageKey("png"));
        given(s3Service.getObjectMetadata(imageKey))
                .willReturn(new S3ObjectMetadata(100L, "image/jpeg", ETAG));
        given(s3Service.getObjectSignature(imageKey, ETAG, 12)).willReturn(PNG_SIGNATURE);

        assertThatThrownBy(() -> chatService.sendImageMessage(GROUP_ID, userPK, imageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        then(chatImageMessageTransactionService).should(never()).persist(anyString(), anyString(), anyString());
        then(s3Service).should().deleteObject(imageKey);
    }

    @Test
    @DisplayName("Content-Type만 위장하고 실제 이미지 시그니처가 아니면 메시지를 저장하지 않는다")
    void sendImageMessage_RejectsInvalidBinarySignature() {
        String temporaryImageKey = temporaryImageKey("png");
        givenImagePreparation(temporaryImageKey, finalImageKey("png"));
        given(s3Service.getObjectMetadata(temporaryImageKey))
                .willReturn(new S3ObjectMetadata(100L, "image/png", ETAG));
        given(s3Service.getObjectSignature(temporaryImageKey, ETAG, 12))
                .willReturn("not-an-image".getBytes());

        assertThatThrownBy(() -> chatService.sendImageMessage(GROUP_ID, userPK, temporaryImageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        then(s3Service).should(never()).copyObjectIfUnchanged(anyString(), anyString(), anyString());
        then(chatImageMessageTransactionService).should(never()).persist(anyString(), anyString(), anyString());
        then(s3Service).should().deleteObject(temporaryImageKey);
    }

    @Test
    @DisplayName("같은 임시 키의 전송 요청이 재시도되면 기존 이미지 메시지를 반환한다")
    void sendImageMessage_ReturnsExistingMessageOnRetry() {
        String temporaryImageKey = temporaryImageKey("png");
        String finalImageKey = finalImageKey("png");
        ChatMessage existingMessage = ChatMessage.builder()
                .chatMessageId(10L)
                .chatRoom(chatRoom)
                .sender(user)
                .messageType(ChatMessageType.IMAGE)
                .imageKey(finalImageKey)
                .createdAt(LocalDateTime.of(2026, 7, 27, 12, 30))
                .build();
        given(chatImageMessageTransactionService.prepare(GROUP_ID, userPK, temporaryImageKey))
                .willReturn(new ChatImageMessageTransactionService.Preparation(finalImageKey, existingMessage));
        given(s3Service.getPresignedGetUrl(finalImageKey)).willReturn("https://s3.example.com/image");

        ChatMessageResponse response = chatService.sendImageMessage(GROUP_ID, userPK, temporaryImageKey);

        assertThat(response.messageId()).isEqualTo(10L);
        assertThat(response.imageKey()).isEqualTo(finalImageKey);
        then(s3Service).should(never()).getObjectMetadata(anyString());
        then(s3Service).should().deleteObject(temporaryImageKey);
        then(chatImageMessageTransactionService).should(never()).persist(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("최종 객체 복사 후 DB 저장이 실패하면 임시 객체와 최종 객체를 정리한다")
    void sendImageMessage_CleansUpCopiedObjectsWhenPersistenceFails() {
        String temporaryImageKey = temporaryImageKey("png");
        String finalImageKey = finalImageKey("png");
        givenImagePreparation(temporaryImageKey, finalImageKey);
        given(s3Service.getObjectMetadata(temporaryImageKey))
                .willReturn(new S3ObjectMetadata(100L, "image/png", ETAG));
        given(s3Service.getObjectSignature(temporaryImageKey, ETAG, 12)).willReturn(PNG_SIGNATURE);
        given(chatImageMessageTransactionService.persist(GROUP_ID, userPK, temporaryImageKey))
                .willThrow(new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));

        assertThatThrownBy(() -> chatService.sendImageMessage(GROUP_ID, userPK, temporaryImageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);

        then(s3Service).should().deleteObject(temporaryImageKey);
        then(s3Service).should().deleteObject(finalImageKey);
    }

    @Test
    @DisplayName("CopyObject가 실패해도 생성 여부가 불확실한 최종 객체와 임시 객체를 정리한다")
    void sendImageMessage_CleansUpObjectsWhenCopyFails() {
        String temporaryImageKey = temporaryImageKey("png");
        String finalImageKey = finalImageKey("png");
        givenImagePreparation(temporaryImageKey, finalImageKey);
        given(s3Service.getObjectMetadata(temporaryImageKey))
                .willReturn(new S3ObjectMetadata(100L, "image/png", ETAG));
        given(s3Service.getObjectSignature(temporaryImageKey, ETAG, 12)).willReturn(PNG_SIGNATURE);
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_ERROR))
                .given(s3Service)
                .copyObjectIfUnchanged(temporaryImageKey, finalImageKey, ETAG);

        assertThatThrownBy(() -> chatService.sendImageMessage(GROUP_ID, userPK, temporaryImageKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_ERROR);

        then(s3Service).should().deleteObject(temporaryImageKey);
        then(s3Service).should().deleteObject(finalImageKey);
        then(chatImageMessageTransactionService).should(never()).persist(anyString(), anyString(), anyString());
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
        given(groupRepository.findByGroupIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.of(chatRoom));
    }

    private void givenPresignedUrlAccess() {
        given(groupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(GROUP_ID, userPK)).willReturn(Optional.of(member));
        given(chatRoomRepository.findByGroup_GroupId(GROUP_ID)).willReturn(Optional.of(chatRoom));
    }

    private void givenImagePreparation(String temporaryImageKey, String finalImageKey) {
        given(chatImageMessageTransactionService.prepare(GROUP_ID, userPK, temporaryImageKey))
                .willReturn(new ChatImageMessageTransactionService.Preparation(finalImageKey, null));
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

    private String temporaryImageKey(String extension) {
        return "chat-temp/" + chatRoom.getChatRoomId()
                + "/" + userPK
                + "/550e8400-e29b-41d4-a716-446655440000."
                + extension;
    }

    private String finalImageKey(String extension) {
        return "chat/" + chatRoom.getChatRoomId()
                + "/" + userPK
                + "/550e8400-e29b-41d4-a716-446655440000."
                + extension;
    }
}
