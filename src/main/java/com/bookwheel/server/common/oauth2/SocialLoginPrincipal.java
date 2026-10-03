package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.auth.AuthRole;

public interface SocialLoginPrincipal {

    String getUserPK();

    AuthRole getRole();

    String getNickname();

    boolean isProfileSet();
}
