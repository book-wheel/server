package com.bookwheel.server.config;

import com.bookwheel.server.common.jwt.JwtAuthenticationFilter;
import com.bookwheel.server.common.jwt.JwtAuthenticationEntryPoint;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import com.bookwheel.server.common.oauth2.apple.AppleClientSecretGenerator;
import com.bookwheel.server.common.oauth2.apple.AppleOAuth2AuthorizedClientRepository;
import com.bookwheel.server.common.oauth2.apple.AppleOAuth2AuthorizationRequestResolver;
import com.bookwheel.server.common.oauth2.apple.AppleOAuthCredentialService;
import com.bookwheel.server.common.oauth2.apple.AppleOAuth2TokenRequestParametersConverter;
import com.bookwheel.server.common.oauth2.handler.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.bookwheel.server.common.oauth2.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenRevocationService accessTokenRevocationService;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AppleClientSecretGenerator appleClientSecretGenerator(
            @Value("${app.oauth2.apple.team-id:}") String teamId,
            @Value("${app.oauth2.apple.key-id:}") String keyId,
            @Value("${app.oauth2.apple.private-key:}") String privateKey
    ) {
        return new AppleClientSecretGenerator(teamId, keyId, privateKey);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            AppleClientSecretGenerator appleClientSecretGenerator,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<AppleOAuthCredentialService> appleOAuthCredentialServiceProvider
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // 탐색 목록은 비로그인 사용자도 볼 수 있도록 GET만 공개한다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/groups", "/api/v1/groups/").permitAll()

                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/reissue",
                                "/api/v1/users/signup",
                                "/login/**",        // 소셜 로그인 콜백 주소 허용
                                "/oauth2/**",       // 시큐리티 기본 소셜 로그인 시작 주소 허용
                                "/api/v1/users/recovery/**",
                                "/images/profiles/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        .requestMatchers(
                                "/api/v1/users/setup-profile",
                                "/api/v1/users/profile-image/presigned-url",
                                "/api/v1/users/check-nickname",
                                "/api/v1/users/logout"
                        ).hasAnyRole("ONBOARDING", "USER")

                        .anyRequest().hasRole("USER")
                )

                .oauth2Login(oauth2 -> {
                    oauth2
                            .userInfoEndpoint(userInfo -> userInfo
                                    .userService(customOAuth2UserService)
                                    .oidcUserService(customOAuth2UserService::loadOidcUser)
                            )
                            .tokenEndpoint(token -> token.accessTokenResponseClient(
                                    appleAwareTokenResponseClient(appleClientSecretGenerator)
                            ))
                            .successHandler(oAuth2SuccessHandler);

                    ClientRegistrationRepository registrations =
                            clientRegistrationRepositoryProvider.getIfAvailable();
                    if (registrations != null) {
                        oauth2.authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(
                                        new AppleOAuth2AuthorizationRequestResolver(registrations)
                                )
                        );
                    }

                    AppleOAuthCredentialService credentialService =
                            appleOAuthCredentialServiceProvider.getIfAvailable();
                    if (credentialService != null) {
                        oauth2.authorizedClientRepository(
                                new AppleOAuth2AuthorizedClientRepository(credentialService)
                        );
                    }
                })
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, accessTokenRevocationService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
    appleAwareTokenResponseClient(AppleClientSecretGenerator appleClientSecretGenerator) {
        RestClientAuthorizationCodeTokenResponseClient client =
                new RestClientAuthorizationCodeTokenResponseClient();

        client.setParametersConverter(
                new AppleOAuth2TokenRequestParametersConverter(appleClientSecretGenerator)
        );
        return client;
    }
}
