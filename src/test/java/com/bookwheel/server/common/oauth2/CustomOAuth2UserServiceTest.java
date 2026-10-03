package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.oauth2.userinfo.OAuth2UserInfo;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OAuth2UserInfo userInfo;

    @InjectMocks
    private CustomOAuth2UserService userService;

    @Test
    @DisplayName("소셜 신규 회원의 외부 프로필 URL을 S3 objectKey 컬럼에 저장하지 않는다")
    void saveUser_DoesNotStoreExternalProfileImageUrl() {
        given(userInfo.getSocialId()).willReturn("google-social-id");
        given(userInfo.getEmail()).willReturn("social@example.com");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        User savedUser = ReflectionTestUtils.invokeMethod(
                userService,
                "saveUser",
                userInfo,
                SocialType.GOOGLE
        );

        then(userRepository).should().save(captor.capture());
        assertThat(savedUser).isSameAs(captor.getValue());
        assertThat(savedUser.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("apple registration ID를 APPLE 소셜 타입으로 변환한다")
    void resolvesAppleSocialType() {
        SocialType socialType = ReflectionTestUtils.invokeMethod(
                userService,
                "getSocialType",
                "apple"
        );

        assertThat(socialType).isEqualTo(SocialType.APPLE);
    }

    @Test
    @DisplayName("Apple 신규 회원의 Private Relay 이메일을 저장한다")
    void saveAppleUser_WithPrivateRelayEmail() {
        given(userInfo.getSocialId()).willReturn("apple-subject");
        given(userInfo.getEmail()).willReturn("relay@privaterelay.appleid.com");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User savedUser = ReflectionTestUtils.invokeMethod(
                userService,
                "saveUser",
                userInfo,
                SocialType.APPLE
        );

        assertThat(savedUser.getSocialType()).isEqualTo(SocialType.APPLE);
        assertThat(savedUser.getSocialId()).isEqualTo("apple-subject");
        assertThat(savedUser.getMail()).isEqualTo("relay@privaterelay.appleid.com");
    }

    @Test
    @DisplayName("Apple ID token을 검증한 후 APPLE 회원을 생성한다")
    void loadOidcUser_CreatesAppleUser() {
        Instant now = Instant.now();
        ClientRegistration registration = appleClientRegistration();
        OidcIdToken idToken = new OidcIdToken(
                "apple-id-token",
                now,
                now.plusSeconds(300),
                Map.of(
                        "iss", "https://appleid.apple.com",
                        "sub", "apple-subject",
                        "aud", "kr.bookwheel.web",
                        "email", "relay@privaterelay.appleid.com"
                )
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "apple-access-token",
                now,
                now.plusSeconds(3600),
                Set.of("openid", "email")
        );
        given(userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, "apple-subject"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        OidcUser result = userService.loadOidcUser(
                new OidcUserRequest(registration, accessToken, idToken)
        );

        then(userRepository).should().save(captor.capture());
        assertThat(result).isInstanceOf(CustomOidcUser.class);
        assertThat(result.getSubject()).isEqualTo("apple-subject");
        assertThat(captor.getValue().getSocialType()).isEqualTo(SocialType.APPLE);
        assertThat(captor.getValue().getMail()).isEqualTo("relay@privaterelay.appleid.com");
    }

    private ClientRegistration appleClientRegistration() {
        return ClientRegistration.withRegistrationId("apple")
                .clientId("kr.bookwheel.web")
                .clientSecret("generated-dynamically")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/apple")
                .scope("openid", "email")
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .tokenUri("https://appleid.apple.com/auth/token")
                .jwkSetUri("https://appleid.apple.com/auth/keys")
                .issuerUri("https://appleid.apple.com")
                .userNameAttributeName("sub")
                .clientName("Apple")
                .build();
    }
}
