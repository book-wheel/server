package com.bookwheel.server.chat.repository;

import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

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
}
