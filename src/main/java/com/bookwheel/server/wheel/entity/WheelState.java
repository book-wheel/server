package com.bookwheel.server.wheel.entity;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.wheel.enums.WheelStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@Table(
        name = "wheel_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wheel_state_round_member", columnNames = {"round_id", "member_id"}),
                @UniqueConstraint(name = "uk_wheel_state_round_ownbook", columnNames = {"round_id", "ownbook_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class WheelState {

    @Id
    @Column(name = "wheel_state_id", length = 50)
    private String wheelStateId;

    @Column(name = "round_id", nullable = false, length = 50)
    private String roundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ownbook_id", nullable = false)
    private OwnBook ownBook;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "wheel_state", nullable = false)
    private WheelStatus wheelState = WheelStatus.READY;

    @Builder.Default
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "review_text", length = 255)
    private String reviewText;

    @Column(name = "auth_image_url", length = 255)
    private String authImageUrl;

    public void updateStatus(WheelStatus wheelState) {
        this.wheelState = wheelState;
    }
}
