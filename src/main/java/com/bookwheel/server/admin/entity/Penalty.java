package com.bookwheel.server.admin.entity;


import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "penalty")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Penalty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 유저가 받은 패널티인지 연결 (N:1 관계)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id_fk")
    private User user;

    @Column(name = "ban_type", nullable = false)
    private String banType;

    @Column(name = "reason_message", nullable = false)
    private String reasonMessage;

    @Column(name = "banned_at", nullable = false)
    private LocalDateTime bannedAt;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    @Builder
    public Penalty(User user, String banType, String reasonMessage, LocalDateTime releaseDate) {
        this.user = user;
        this.banType = banType;
        this.reasonMessage = reasonMessage;
        this.bannedAt = LocalDateTime.now();
        this.releaseDate = releaseDate;
    }
}
