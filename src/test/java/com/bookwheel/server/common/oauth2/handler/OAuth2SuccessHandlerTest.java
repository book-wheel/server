package com.bookwheel.server.common.oauth2.handler;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

class OAuth2SuccessHandlerTest {

    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenRepository refreshTokenRepository;
    private OAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        successHandler = new OAuth2SuccessHandler(
                jwtTokenProvider,
                refreshTokenRepository,
                "bookwheel://oauth2/redirect"
        );
    }

    @Test
    @DisplayName("소셜 로그인 성공 시 설정된 앱 주소로 토큰을 저장하고 리다이렉트한다")
    void redirectsToConfiguredAppUriAndStoresRefreshToken() throws Exception {
        CustomOAuth2User oAuth2User = createOAuth2User("USER_temporary");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                oAuth2User,
                null,
                oAuth2User.getAuthorities()
        );
        given(jwtTokenProvider.createAccessToken("user-pk", AuthRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken("user-pk", AuthRole.USER)).willReturn("refresh-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "bookwheel://oauth2/redirect?accessToken=access-token&refreshToken=refresh-token&isFirstLogin=true"
        );

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserPK()).isEqualTo("user-pk");
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("기존 사용자는 최초 로그인 여부로 false를 전달한다")
    void redirectsExistingUserWithFirstLoginFalse() throws Exception {
        CustomOAuth2User oAuth2User = createOAuth2User("책바퀴");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                oAuth2User,
                null,
                oAuth2User.getAuthorities()
        );
        given(jwtTokenProvider.createAccessToken("user-pk", AuthRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken("user-pk", AuthRole.USER)).willReturn("refresh-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).endsWith("isFirstLogin=false");
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
