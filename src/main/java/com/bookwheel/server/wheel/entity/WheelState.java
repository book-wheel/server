package com.bookwheel.server.wheel.entity;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.common.exception.*;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.wheel.enums.WheelStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "review_date")
    private LocalDateTime reviewedAt;

    // 독서 상태가 지워질 때 사진 데이터도 전부 지움
    @Builder.Default
    @OneToMany(mappedBy = "wheelState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WheelStateImage> authImages = new ArrayList<>();

    public void updateStatus(WheelStatus wheelState) {
        this.wheelState = wheelState;
    }

    public void activate() {
        if (this.wheelState != WheelStatus.PLANNED) {
            return;
        }
        this.wheelState = WheelStatus.READY;
    }

    public void complete(String reviewText, List<String> objectKeys) {
        // 이미 정보가 존재한다면 실행 X
        if (this.isCompleted) {
            throw new BusinessException(ErrorCode.WHEEL_ALREADY_CERTIFIED);
        }
        if (this.wheelState != WheelStatus.READY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 이미지 정보가 없다면 실행 X
        if (objectKeys == null || objectKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGES_NOT_FOUND);
        }

        // 1. 감상평 저장 및 상태 변경
        this.reviewText = reviewText;
        this.wheelState = WheelStatus.COMPLETED;
        this.isCompleted = true;
        this.reviewedAt = LocalDateTime.now();

        // 2. 저장
        List<WheelStateImage> images = objectKeys.stream()
                .map(objectKey -> WheelStateImage.of(objectKey, this))
                .toList();

        this.authImages.addAll(images);
    }
}
