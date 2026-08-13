package com.bookwheel.server.notification.push;

import java.util.regex.Pattern;

public final class ExpoPushToken {

    // Expo clients may issue either the current or legacy prefix.
    public static final String REGEX = "^Expo(?:nent)?PushToken\\[[A-Za-z0-9_-]+]$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private ExpoPushToken() {
    }

    public static boolean isValid(String token) {
        return token != null && PATTERN.matcher(token).matches();
    }
}
