package com.bookwheel.server.group.dto.search;

import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;

// 그룹 목록 조회 조건 (GET 쿼리 파라미터)
public record GroupSearchCondition(
        State state,     // 모집 상태
        String type,     // ONLINE, OFFLINE
        Region region,   // SEOUL, BUSAN 등
        String keyword   // 그룹명 검색어
) {
}
