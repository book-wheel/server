package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.auth.AuthRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

@Getter
public class CustomOidcUser extends DefaultOidcUser implements SocialLoginPrincipal {

    private final String userPK;
    private final AuthRole role;
    private final String nickname;
    private final boolean profileSet;

    public CustomOidcUser(
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            OidcUserInfo userInfo,
            String nameAttributeKey,
            String userPK,
            AuthRole role,
            String nickname,
            boolean profileSet
    ) {
        super(authorities, idToken, userInfo, nameAttributeKey);
        this.userPK = userPK;
        this.role = role;
        this.nickname = nickname;
        this.profileSet = profileSet;
    }
}
