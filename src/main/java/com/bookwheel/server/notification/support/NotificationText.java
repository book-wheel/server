package com.bookwheel.server.notification.support;

/**
 * 알림 title/body 에 사용자 입력을 끼워 넣기 전 sanitize 하는 유틸.
 * - 위험 태그 문자(<, >) 제거 (strip 방식)
 * - 개행/제어문자 제거
 * - 길이 cap (DB 컬럼 한도 위반 방지)
 *
 * 알림 본문은 모바일 푸시/인앱 등 plain-text 컨텍스트에서 그대로 렌더되므로
 * HTML escape 대신 위험 문자만 제거한다. 이메일/관리자 대시보드 등 HTML 컨텍스트로
 * 노출할 때는 출력 단계에서 별도 escape 가 필요하다.
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
        StringBuilder out = new StringBuilder(Math.min(raw.length(), maxLength));
        for (int i = 0; i < raw.length() && out.length() < maxLength; i++) {
            char c = raw.charAt(i);
            if (c < 0x20) {
                continue;
            }
            if (c == '<' || c == '>') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}