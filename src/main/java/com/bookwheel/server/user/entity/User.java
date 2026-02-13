package com.bookwheel.server.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.bookwheel.server.user.entity.Role;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "user_id", length = 50, unique = true, nullable = false)
    private String userId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    @Column(name = "mail", length = 100, nullable = false)
    private String mail;

    @Enumerated(EnumType.STRING)
    @Column(name = "social", length = 20)
    private SocialType social = SocialType.NONE;

    @Column(name = "social_id", length = 100)
    private String socialId;

    @Column(name = "comment")
    private String comment;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Builder
    public User(String userId, String password, String nickname, String mail,
                SocialType social, String socialId, String comment, String profileImage,
                Role role) { // ✨ 파라미터에 role 추가!
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.password = password;
        this.nickname = nickname;
        this.mail = mail;
        this.social = social != null ? social : SocialType.NONE;
        this.socialId = socialId;
        this.comment = comment;
        this.profileImage = profileImage;
        this.isActive = true;

        // 기본적으로 USER로 설정
        this.role = role != null ? role : Role.USER;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateProfile(String nickname, String comment, String profileImage) {
        this.nickname = nickname;
        this.comment = comment;
        this.profileImage = profileImage;
    }

    public void deactivate() {
        this.isActive = false;
    }
}