package com.bookwheel.server.common.oauth2.apple;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.oauth2.apple.token-encryption")
public class AppleTokenEncryptionProperties {

    private String activeKeyVersion;
    private Map<String, String> keys = new LinkedHashMap<>();
}
