package com.bookwheel.server.common.oauth2.userinfo;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getSocialId() { return String.valueOf(attributes.get("id")); }

    @Override
    public String getNickname() {
        Map<String, Object> profile = (Map<String, Object>) getKakaoAccount().get("profile");
        return profile == null ? null : (String) profile.get("nickname");
    }

    @Override
    public String getEmail() { return (String) getKakaoAccount().get("email"); }

    @Override
    public String getProfileImage() {
        Map<String, Object> profile = (Map<String, Object>) getKakaoAccount().get("profile");
        return profile == null ? null : (String) profile.get("thumbnail_image_url");
    }

    private Map<String, Object> getKakaoAccount() {
        return (Map<String, Object>) attributes.get("kakao_account");
    }
}