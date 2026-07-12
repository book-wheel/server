package com.bookwheel.server.chat.repository;

import com.bookwheel.server.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    Optional<ChatRoom> findByGroup_GroupId(String groupId);

    boolean existsByGroup_GroupId(String groupId);
}
