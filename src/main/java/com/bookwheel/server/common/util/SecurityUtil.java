package com.bookwheel.server.common.util;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUtil {
    public static String getUserId(Object principal) {
        if (principal instanceof CustomOAuth2User oauth2User) return oauth2User.getUserId();
        if (principal instanceof UserDetails userDetails) return userDetails.getUsername();
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}