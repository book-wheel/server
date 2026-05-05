package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.auth.AuthRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomOAuth2User extends DefaultOAuth2User {

    private String userPK;
    private AuthRole role;
    private String nickname;

    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities,
                            Map<String, Object> attributes, String nameAttributeKey,
                            String userPK, AuthRole role, String nickname) {
        super(authorities, attributes, nameAttributeKey);
        this.userPK = userPK;
        this.role = role;
        this.nickname = nickname;
        }
}
