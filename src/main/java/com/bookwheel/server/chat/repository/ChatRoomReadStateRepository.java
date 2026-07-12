package com.bookwheel.server.chat.repository;

import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.entity.ChatRoomReadState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomReadStateRepository extends JpaRepository<ChatRoomReadState, Long> {

    Optional<ChatRoomReadState> findByChatRoomAndUser_Id(ChatRoom chatRoom, String userPK);

    boolean existsByChatRoomAndUser_Id(ChatRoom chatRoom, String userPK);
}
