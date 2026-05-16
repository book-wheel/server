package com.bookwheel.server.notification.support;

/**
 * 알림 title/body 에 사용자 입력을 끼워 넣기 전 sanitize 하는 유틸.
 * - HTML 메타문자(&, <, >, ", ')를 안전 형태로 치환
 * - 개행/제어문자 제거
 * - 길이 cap (DB 컬럼 한도 위반 방지)
 *
 * 클라이언트가 plain-text 로 렌더하더라도 admin dashboard / 이메일 등 HTML 컨텍스트에
 * 동일 본문이 그대로 흐를 가능성을 차단하기 위해 서버에서 한 차례 정제한다.
 */
public final class NotificationText {

    private NotificationText() {}

    public static String safe(String raw) {
        return safe(raw, 100);
    }

    public static String safe(String raw, int maxLength) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length() && out.length() < maxLength; i++) {
            char c = raw.charAt(i);
            // 제어문자/개행 제거
            if (c < 0x20) {
                continue;
            }
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}