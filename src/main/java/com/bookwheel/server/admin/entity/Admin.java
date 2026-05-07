package com.bookwheel.server.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin {

    @Id
    @Column(name = "admin_pk", length = 50)
    private String adminPK;

    @Column(name = "login_id", length = 50, unique = true, nullable = false)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder
    public Admin(String loginId, String password, String name, Boolean isActive) {
        this.adminPK = UUID.randomUUID().toString();
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.isActive = isActive != null ? isActive : true;
    }
}
