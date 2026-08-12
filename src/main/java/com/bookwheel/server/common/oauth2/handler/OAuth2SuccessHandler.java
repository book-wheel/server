package com.bookwheel.server.common.oauth2.handler;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import com.bookwheel.server.common.oauth2.OAuth2LoginCodeService;
import com.bookwheel.server.common.oauth2.OAuth2Pkce;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2LoginCodeService loginCodeService;
    private final String redirectUri;

    public OAuth2SuccessHandler(
            OAuth2LoginCodeService loginCodeService,
            @Value("${app.oauth2.redirect-uri}") String redirectUri
    ) {
        this.loginCodeService = loginCodeService;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        log.info("OAuth2 로그인 성공! 일회용 로그인 코드를 발급합니다.");

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        String userPK = oAuth2User.getUserPK();
        AuthRole role = oAuth2User.getRole();

        HttpSession session = request.getSession(false);
        Object codeChallengeAttribute = session == null
                ? null
                : session.getAttribute(OAuth2Pkce.SESSION_ATTRIBUTE);
        String codeChallenge = codeChallengeAttribute instanceof String value ? value : null;

        if (!OAuth2Pkce.isValidCodeChallenge(codeChallenge)) {
            log.warn("OAuth2 로그인에 필요한 PKCE code challenge가 없습니다.");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "PKCE code challenge is required");
            return;
        }
        session.removeAttribute(OAuth2Pkce.SESSION_ATTRIBUTE);

        // 소셜 신규 유저인지 판단
        boolean isFirstLogin = oAuth2User.getNickname().startsWith("USER_");
        String code = loginCodeService.issue(userPK, role, isFirstLogin, codeChallenge);

        // 토큰 대신 PKCE로 보호된 일회용 코드만 프론트엔드로 전달한다.
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .build().toUriString();

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
