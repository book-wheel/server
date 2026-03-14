package com.bookwheel.server.common.oauth2.userinfo;

import java.util.Map;

public class GoogleOAuth2UserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getSocialId() { return (String) attributes.get("sub"); }

    @Override
    public String getNickname() { return (String) attributes.get("name"); }

    @Override
    public String getEmail() { return (String) attributes.get("email"); }

    @Override
    public String getProfileImage() { return (String) attributes.get("picture"); }
}