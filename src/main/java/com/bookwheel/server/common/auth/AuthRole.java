package com.bookwheel.server.common.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthRole {
    ONBOARDING("ROLE_ONBOARDING"),
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN");

    private final String key;
}
