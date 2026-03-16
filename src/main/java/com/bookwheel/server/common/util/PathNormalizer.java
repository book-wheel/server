package com.bookwheel.server.common.util;

import java.util.List;
import java.util.Objects;

/**
 * S3 경로 세그먼트 또는 URL 형태의 입력을 정규화하기 위한 유틸리티 클래스입니다.
 * 공백을 제거하고 앞뒤의 슬래시를 제거하여, 호출자가 이중 구분자(double separators)에
 * 대한 걱정 없이 세그먼트들을 안전하게 연결할 수 있도록 합니다.
 */
public final class PathNormalizer {

    private PathNormalizer() {
        // 유틸리티 클래스 (인스턴스화 방지)
    }

    public static String normalizeSegment(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        // 의도치 않은 절대 경로가 생성되는 것을 방지하기 위해 앞뒤의 슬래시를 모두 제거
        return trimmed.replaceAll("^/+|/+$", "");
    }

    public static String normalizeFileName(String raw) {
        return normalizeSegment(raw);
    }

    public static List<String> normalizeUrls(List<String> urls) {
        if (urls == null) {
            return List.of();
        }
        return urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                // 앞뒤의 공백 및 슬래시 제거
                .map(url -> url.replaceAll("^\\s*/+|/+$", ""))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}