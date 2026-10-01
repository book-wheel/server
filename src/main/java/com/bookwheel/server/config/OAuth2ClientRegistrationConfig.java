package com.bookwheel.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.apple.client-id")
public class OAuth2ClientRegistrationConfig {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        Map<String, ClientRegistration> registrations = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations();

        List<ClientRegistration> configuredRegistrations = new ArrayList<>(registrations.size());
        registrations.forEach((registrationId, registration) -> {
            if ("apple".equals(registrationId)) {
                configuredRegistrations.add(ClientRegistration.withClientRegistration(registration)
                        .issuerUri(APPLE_ISSUER)
                        .build());
                return;
            }
            configuredRegistrations.add(registration);
        });

        return new InMemoryClientRegistrationRepository(configuredRegistrations);
    }
}
