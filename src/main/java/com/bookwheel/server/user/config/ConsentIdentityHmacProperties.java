package com.bookwheel.server.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "user.consent.identity-hmac")
public class ConsentIdentityHmacProperties {

    private String activeKeyVersion;
    private Map<String, String> keys = new LinkedHashMap<>();
}
