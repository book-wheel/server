package com.bookwheel.server.common.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("탈퇴로 폐기된 회원의 유효한 JWT도 인증에 사용하지 않는다")
    void rejectsRevokedUserToken() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        AccessTokenRevocationService revocationService = mock(AccessTokenRevocationService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, revocationService);
        var authentication = new UsernamePasswordAuthenticationToken(
                "user-pk", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        given(tokenProvider.validateToken("token")).willReturn(true);
        given(tokenProvider.getAuthentication("token")).willReturn(authentication);
        given(revocationService.isRevoked("user-pk")).willReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
