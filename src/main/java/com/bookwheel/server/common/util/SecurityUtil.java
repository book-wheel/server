package com.bookwheel.server.common.util;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUtil {
    public static String getUserPK(Object principal) {
        if (principal instanceof CustomOAuth2User oauth2User) return oauth2User.getUserPK();
        if (principal instanceof UserDetails userDetails) return userDetails.getUsername();
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    // 인증이 선택인 조회에서는 비로그인 사용자를 null userPK로 처리한다.
    public static String getUserPKOrNull(Object principal) {
        if (principal == null || "anonymousUser".equals(principal)) {
            return null;
        }
        if (principal instanceof CustomOAuth2User oauth2User) return oauth2User.getUserPK();
        if (principal instanceof UserDetails userDetails) return userDetails.getUsername();
        return null;
    }
}