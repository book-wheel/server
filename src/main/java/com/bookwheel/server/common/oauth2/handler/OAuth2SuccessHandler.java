package com.bookwheel.server.common.oauth2.handler;

import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import com.bookwheel.server.user.entity.Role;
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
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        log.info("OAuth2 로그인 성공! JWT 토큰 발행을 시작합니다.");

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        String userId = oAuth2User.getUserId();
        Role role = oAuth2User.getRole();

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(userId, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, role);

        // 프론트엔드로 리다이렉트
        // 실제 운영 시 쿠키나 특정 URL을 사용. 현재는 테스트를 위해 파라미터 방식 사용
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}