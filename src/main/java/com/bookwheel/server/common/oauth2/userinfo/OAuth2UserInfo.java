package com.bookwheel.server.common.oauth2.userinfo;

public interface OAuth2UserInfo {
    String getSocialId();     // 소셜 고유 ID (PK 대용)
    String getEmail();        // 이메일
}
