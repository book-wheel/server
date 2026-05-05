package com.bookwheel.server.common.oauth2.handler;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {        log.info("OAuth2 로그인 성공! JWT 토큰 발행을 시작합니다.");

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        String userPK = oAuth2User.getUserPK();
        AuthRole role = oAuth2User.getRole();

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(userPK, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(userPK, role);

        // 소셜 신규 유저인지 판단
        boolean isFirstLogin = oAuth2User.getNickname().startsWith("USER_");

        // 프론트엔드로 리다이렉트
        // [TODO] 배포 시 리다이렉트 URL은 실제 도메인 주소로 변경해야 함
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("isFirstLogin", isFirstLogin)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
