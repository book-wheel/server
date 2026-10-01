package com.bookwheel.server.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "user_block",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_block_pair",
        columnNames = {"blocker_user_pk", "blocked_user_pk"}
    ),
    indexes = {
        @Index(name = "idx_user_block_list", columnList = "blocker_user_pk,created_at,block_id"),
        @Index(name = "idx_user_block_target", columnList = "blocked_user_pk")
    }
)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long blockId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_user_pk", nullable = false,
        foreignKey = @ForeignKey(name = "fk_user_block_blocker"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_user_pk", nullable = false,
        foreignKey = @ForeignKey(name = "fk_user_block_blocked"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User blocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserBlock(User blocker, User blocked) {
        this.blocker = Objects.requireNonNull(blocker, "차단 요청자는 필수입니다.");
        this.blocked = Objects.requireNonNull(blocked, "차단 대상은 필수입니다.");
        if (Objects.equals(blocker.getId(), blocked.getId())) {
            throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다.");
        }
    }

    public static UserBlock create(User blocker, User blocked) {
        return new UserBlock(blocker, blocked);
    }
}
