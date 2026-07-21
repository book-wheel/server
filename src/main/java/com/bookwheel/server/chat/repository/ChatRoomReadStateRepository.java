package com.bookwheel.server.chat.repository;

import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.entity.ChatRoomReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomReadStateRepository extends JpaRepository<ChatRoomReadState, Long> {

    Optional<ChatRoomReadState> findByChatRoomAndUser_Id(ChatRoom chatRoom, String userPK);

    boolean existsByChatRoomAndUser_Id(ChatRoom chatRoom, String userPK);

    @Modifying
    @Query("delete from ChatRoomReadState readState where readState.chatRoom = :chatRoom")
    // 메시지보다 먼저 읽음 상태를 제거해 마지막 읽은 메시지 FK 충돌을 막는다.
    void deleteAllByChatRoom(@Param("chatRoom") ChatRoom chatRoom);
}
