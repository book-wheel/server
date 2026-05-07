package com.bookwheel.server.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "login_id", length = 50, unique = true, nullable = false)
    private String loginId;

    @Column(name = "password")
    private String password;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    @Column(name = "mail", length = 100, nullable = false)
    private String mail;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", columnDefinition = "VARCHAR(20) DEFAULT 'NONE'")
    private SocialType socialType = SocialType.NONE;

    @Column(name = "social_id", length = 100)
    private String socialId;

    @Column(name = "comment")
    private String comment;

    @Column(name = "profile_image_key")
    private String profileImageKey;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "ban_expired_at")
    private LocalDateTime banExpiredAt;

    @Column(name = "is_profile_set")
    private Boolean isProfileSet = false;

    @Builder
    public User(String loginId, String password, String nickname, String mail,
                SocialType socialType, String socialId, String comment, String profileImageKey,
                Boolean isActive) {
        this.id = UUID.randomUUID().toString();
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.mail = mail;
        this.socialType = socialType != null ? socialType : SocialType.NONE;
        this.socialId = socialId;
        this.comment = comment;
        this.profileImageKey = profileImageKey;
        this.isActive = isActive != null ? isActive : true;
    }

    public void completeProfile() {
        this.isProfileSet = true;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateProfile(String nickname, String comment, String profileImageKey) {
        this.nickname = nickname;
        this.comment = comment;
        this.profileImageKey = profileImageKey;
    }

    public void deactivate() {
        this.isActive = false;
        this.nickname = "탈퇴한 사용자_" + java.util.UUID.randomUUID().toString().substring(0, 8);    // 닉네임 중복 방지
        this.comment = null;
        this.profileImageKey = null;
        this.password = "DELETED_USER_" + java.util.UUID.randomUUID();
    }

    public void applyBan(String banType) {
        if ("PERMANENT".equals(banType)) {
            this.banExpiredAt = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
        }else if ("SEVEN_DAYS".equals(banType)) {
            this.banExpiredAt = LocalDateTime.now().plusDays(7);
        }else if ("THREE_DAYS".equals(banType)) {
            this.banExpiredAt = LocalDateTime.now().plusDays(3);
        }

    }

    public String getBanStatus() {
        if (this.banExpiredAt == null || LocalDateTime.now().isAfter(this.banExpiredAt)) {
            return "ACTIVE"; //정지기록 x, 이미 기간 지남
        }
        if (this.banExpiredAt.getYear() == 9999) {
            return "PERMANENT_BANNED"; // 영구 정지
        }
        return "BANNED"; // 기간제 정지 중
    }
}
