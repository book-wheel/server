package com.bookwheel.server.chat.entity;

import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(
        name = "chat_room_read_state",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_room_read_state_room_user",
                        columnNames = {"chat_room_id", "user_pk"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_read_state_id")
    private Long chatRoomReadStateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_pk", referencedColumnName = "id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private ChatMessage lastReadMessage;

    @UpdateTimestamp
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    public boolean advanceLastReadMessage(ChatMessage candidateMessage) {
        if (lastReadMessage != null
                && candidateMessage.getChatMessageId() <= lastReadMessage.getChatMessageId()) {
            return false;
        }

        this.lastReadMessage = candidateMessage;
        return true;
    }
}
