package com.bookwheel.server.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Builder
    public User(String userId, String password, String nickname, String mail,
                SocialType social, String socialId, String comment, String profileImage) {
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