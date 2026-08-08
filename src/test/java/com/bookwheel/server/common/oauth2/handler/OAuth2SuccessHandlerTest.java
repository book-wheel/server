package com.bookwheel.server.common.oauth2.handler;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import com.bookwheel.server.common.oauth2.OAuth2LoginCodeService;
import com.bookwheel.server.common.oauth2.OAuth2Pkce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OAuth2SuccessHandlerTest {

    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private OAuth2LoginCodeService loginCodeService;
    private OAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        loginCodeService = mock(OAuth2LoginCodeService.class);
        successHandler = new OAuth2SuccessHandler(
                loginCodeService,
                "bookwheel://auth/callback"
        );
    }

    @Test
    @DisplayName("소셜 로그인 성공 시 토큰 대신 일회용 코드만 앱으로 전달한다")
    void redirectsToAppWithOneTimeCodeOnly() throws Exception {
        CustomOAuth2User oAuth2User = createOAuth2User("USER_temporary");
        Authentication authentication = createAuthentication(oAuth2User);
        given(loginCodeService.issue("user-pk", AuthRole.USER, true, CODE_CHALLENGE))
                .willReturn("one-time-code");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(OAuth2Pkce.SESSION_ATTRIBUTE, CODE_CHALLENGE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("bookwheel://auth/callback?code=one-time-code")
                .doesNotContain("accessToken", "refreshToken", "isFirstLogin");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getSession().getAttribute(OAuth2Pkce.SESSION_ATTRIBUTE)).isNull();
        verify(loginCodeService).issue("user-pk", AuthRole.USER, true, CODE_CHALLENGE);
    }

    @Test
    @DisplayName("기존 사용자의 일회용 코드에는 최초 로그인 여부 false를 저장한다")
    void issuesCodeForExistingUserWithFirstLoginFalse() throws Exception {
        CustomOAuth2User oAuth2User = createOAuth2User("책바퀴");
        Authentication authentication = createAuthentication(oAuth2User);
        given(loginCodeService.issue("user-pk", AuthRole.USER, false, CODE_CHALLENGE))
                .willReturn("one-time-code");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(OAuth2Pkce.SESSION_ATTRIBUTE, CODE_CHALLENGE);

        successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication);

        verify(loginCodeService).issue("user-pk", AuthRole.USER, false, CODE_CHALLENGE);
    }

    @Test
    @DisplayName("PKCE challenge가 없으면 일회용 코드를 발급하지 않는다")
    void rejectsLoginWithoutPkceChallenge() throws Exception {
        CustomOAuth2User oAuth2User = createOAuth2User("책바퀴");
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                createAuthentication(oAuth2User)
        );

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getRedirectedUrl()).isNull();
        verify(loginCodeService, never()).issue(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private Authentication createAuthentication(CustomOAuth2User oAuth2User) {
        return new UsernamePasswordAuthenticationToken(
                oAuth2User,
                null,
                oAuth2User.getAuthorities()
        );
    }

    private CustomOAuth2User createOAuth2User(String nickname) {
        return new CustomOAuth2User(
                List.of(new SimpleGrantedAuthority(AuthRole.USER.getKey())),
                Map.of("id", "social-id"),
                "id",
                "user-pk",
                AuthRole.USER,
                nickname
        );
    }
}
