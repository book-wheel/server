package com.bookwheel.server.notification.push;

import java.util.regex.Pattern;

public final class ExpoPushToken {

    // Expo의 현재·레거시 prefix 형식과 공식 서버 SDK가 인정하는 UUID 형식을 모두 허용한다.
    public static final String REGEX = "^(?:Expo(?:nent)?PushToken\\[[^\\[\\]]+]|"
            + "[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12})$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private ExpoPushToken() {
    }

    public static boolean isValid(String token) {
        return token != null && PATTERN.matcher(token).matches();
    }
}
