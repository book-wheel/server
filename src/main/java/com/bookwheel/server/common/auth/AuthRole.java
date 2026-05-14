package com.bookwheel.server.common.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthRole {
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN");

    private final String key;
}
