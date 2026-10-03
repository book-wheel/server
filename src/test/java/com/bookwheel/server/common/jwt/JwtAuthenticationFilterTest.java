package com.bookwheel.server.common.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.bookwheel.server.common.auth.AuthRole;
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
import static org.mockito.Mockito.verifyNoInteractions;

class JwtAuthenticationFilterTest {

    private final UserAuthenticationStatusService userStatus = mock(UserAuthenticationStatusService.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("탈퇴로 폐기된 회원의 유효한 JWT도 인증에 사용하지 않는다")
    void rejectsRevokedUserToken() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        AccessTokenRevocationService revocationService = mock(AccessTokenRevocationService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, revocationService, userStatus);
        given(userStatus.canAuthenticate("user-pk")).willReturn(true);
        var authentication = new UsernamePasswordAuthenticationToken(
                "user-pk", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        given(tokenProvider.validateToken("token")).willReturn(true);
        given(tokenProvider.isAuthenticationToken("token")).willReturn(true);
        given(tokenProvider.getAuthentication("token")).willReturn(authentication);
        given(revocationService.isRevoked("user-pk")).willReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(revocationService).isRevoked("user-pk");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Refresh Token은 일반 API 인증에 사용하지 않는다")
    void rejectsRefreshTokenForApiAuthentication() throws Exception {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(
                "dGVzdC1qd3Qtc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcy1sb25n"
        );
        AccessTokenRevocationService revocationService = mock(AccessTokenRevocationService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, revocationService, userStatus);
        String refreshToken = tokenProvider.createRefreshToken(
                "user-pk", com.bookwheel.server.common.auth.AuthRole.USER
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(revocationService, userStatus);
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsDatabaseBlockedUserWithoutConsultingRedis() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(
                "dGVzdC1qd3Qtc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcy1sb25n");
        AccessTokenRevocationService revocation = mock(AccessTokenRevocationService.class);
        String token = provider.createAccessToken("user-pk", com.bookwheel.server.common.auth.AuthRole.USER);
        given(userStatus.canAuthenticate("user-pk")).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token);

        new JwtAuthenticationFilter(provider, revocation, userStatus)
                .doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(revocation);
    }

    @Test
    void adminTokenDoesNotRequireUserTableEntry() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(
                "dGVzdC1qd3Qtc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcy1sb25n");
        AccessTokenRevocationService revocation = mock(AccessTokenRevocationService.class);
        String token = provider.createAccessToken("admin-pk", com.bookwheel.server.common.auth.AuthRole.ADMIN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/reports");
        request.addHeader("Authorization", "Bearer " + token);

        new JwtAuthenticationFilter(provider, revocation, userStatus)
                .doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin-pk");
        verifyNoInteractions(userStatus);
        verify(revocation).isRevoked("admin-pk");
    }

    @ParameterizedTest
    @EnumSource(value = AuthRole.class, names = {"USER", "ONBOARDING"})
    void activeUserRetainsTokenAuthorities(AuthRole role) throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(
                "dGVzdC1qd3Qtc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcy1sb25n");
        AccessTokenRevocationService revocation = mock(AccessTokenRevocationService.class);
        given(userStatus.canAuthenticate("user-pk")).willReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + provider.createAccessToken("user-pk", role));

        new JwtAuthenticationFilter(provider, revocation, userStatus)
                .doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("user-pk");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly(role.getKey());
        verify(userStatus).canAuthenticate("user-pk");
        verify(revocation).isRevoked("user-pk");
    }
}
