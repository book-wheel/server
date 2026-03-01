package com.bookwheel.server.common.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // 소셜 로그인 관련 경로는 JWT 검사 스킵
        log.info("현재 들어온 요청 경로: {}", path);

        if (path.startsWith("/api/v1/auth") || path.startsWith("/oauth2") ||
                path.startsWith("/login") || path.startsWith("/favicon.ico")) {
            log.info("JWT 필터 통과 (허용된 경로): {}", path);
            filterChain.doFilter(request, response);
            return;
        }

    // Request Header에서 토큰 추출
    String token = resolveToken(request);

        if(token !=null&&jwtTokenProvider.validateToken(token))

    {
        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        // 스프링 시큐리티 저장소(ContextHolder)에 인증됨 저장
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 다음 필터로 넘기기
        filterChain.doFilter(request,response);
}


    // 순수 토큰만
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}