package com.bookwheel.server.common.oauth2.handler;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.oauth2.OAuth2LoginCodeService;
import com.bookwheel.server.common.oauth2.OAuth2Pkce;
import com.bookwheel.server.common.oauth2.SocialLoginPrincipal;
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

        if (!(authentication.getPrincipal() instanceof SocialLoginPrincipal socialPrincipal)) {
            log.error("소셜 로그인 principal 타입이 올바르지 않습니다.");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid social login principal");
            return;
        }
        String userPK = socialPrincipal.getUserPK();
        AuthRole role = socialPrincipal.getRole();

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

        // 임시 닉네임 규칙이 아니라 저장된 프로필 완료 상태로 소셜 신규 유저를 판단한다.
        boolean isFirstLogin = !socialPrincipal.isProfileSet();
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
