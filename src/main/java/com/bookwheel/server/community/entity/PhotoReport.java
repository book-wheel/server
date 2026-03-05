package com.bookwheel.server.community.entity;

import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;

@Entity
public class PhotoReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Bookphoto photo; // 신고 대상 사진

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter; // 신고자

    private String reason; // 신고 사유
}
