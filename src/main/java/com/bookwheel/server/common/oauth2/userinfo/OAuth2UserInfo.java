package com.bookwheel.server.common.oauth2.userinfo;

import java.util.Map;

public interface OAuth2UserInfo {
    String getSocialId();     // 소셜 고유 ID (PK 대용)
    String getEmail();        // 이메일
    String getProfileImage(); // 프로필 사진 URL
}