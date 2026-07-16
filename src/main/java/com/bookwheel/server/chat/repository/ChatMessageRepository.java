package com.bookwheel.server.chat.repository;

import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = "sender")
    Slice<ChatMessage> findByChatRoomAndChatMessageIdGreaterThanOrderByChatMessageIdAsc(
            ChatRoom chatRoom,
            Long chatMessageId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "sender")
    Slice<ChatMessage> findByChatRoomOrderByChatMessageIdAsc(
            ChatRoom chatRoom,
            Pageable pageable
    );

    Optional<ChatMessage> findByChatMessageIdAndChatRoom(Long chatMessageId, ChatRoom chatRoom);

    long countByChatRoom(ChatRoom chatRoom);

    long countByChatRoomAndChatMessageIdGreaterThan(ChatRoom chatRoom, Long chatMessageId);

    // S3 이미지 키를 수집한 뒤 채팅 메시지를 삭제하기 위해 전체 메시지를 조회한다.
    List<ChatMessage> findByChatRoom(ChatRoom chatRoom);

    @Modifying
    @Query("delete from ChatMessage message where message.chatRoom = :chatRoom")
    // 채팅방을 삭제하기 전에 해당 방의 메시지를 모두 제거한다.
    void deleteAllByChatRoom(@Param("chatRoom") ChatRoom chatRoom);
}
